package com.example.sample;

import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

import com.example.courses.CoursesApplication;
import com.example.students.StudentsApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DatabasePerServiceSampleRunnerTest {
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.1-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(10))
            .withUsername("testuser")
            .withPassword("testpwd");

    private ConfigurableApplicationContext studentsContext;
    private ConfigurableApplicationContext coursesContext;

    @BeforeAll
    static void setUpContainer() throws Exception {
        oracle.start();
        oracle.copyFileToContainer(
                MountableFile.forClasspathResource("create-pdbs.sql"),
                "/tmp/create-pdbs.sql"
        );
        var result = oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/create-pdbs.sql");
        assertThat(result.getExitCode())
                .withFailMessage("PDB setup failed.%nSTDOUT:%n%s%nSTDERR:%n%s", result.getStdout(), result.getStderr())
                .isZero();
        waitForDatabase("studentpdb", "students_app");
        waitForDatabase("coursepdb", "courses_app");
    }

    @BeforeEach
    void resetData() throws Exception {
        truncateStudentsData();
        truncateCoursesData();
    }

    @AfterEach
    void stopApps() {
        if (studentsContext != null) {
            studentsContext.close();
            studentsContext = null;
        }
        if (coursesContext != null) {
            coursesContext.close();
            coursesContext = null;
        }
    }

    @Test
    void eligibleStudentPassesEligibilityChecksAcrossTwoServices() throws Exception {
        DatabasePerServiceSampleRunner.SampleReport report = runScenario(
                DatabasePerServiceSampleRunner.ScenarioRequest.eligible("eligible")
        );

        assertThat(report.decision().eligible()).isTrue();
        assertThat(report.decision().prerequisitesSatisfied()).isTrue();
        assertThat(report.decision().holdsClear()).isTrue();
        assertThat(report.decision().seatsAvailable()).isTrue();
        assertThat(report.studentsDatabase().container()).isEqualTo("STUDENTPDB");
        assertThat(report.coursesDatabase().container()).isEqualTo("COURSEPDB");
        assertThat(report.studentsDatabase().rowCount()).isEqualTo(3L);
        assertThat(report.coursesDatabase().rowCount()).isEqualTo(4L);
        assertThat(report.render()).contains("Decision: ELIGIBLE");
        assertThat(report.render()).contains("STUDENTPDB");
        assertThat(report.render()).contains("COURSEPDB");
    }

    @Test
    void academicHoldMakesStudentIneligible() throws Exception {
        DatabasePerServiceSampleRunner.SampleReport report = runScenario(
                DatabasePerServiceSampleRunner.ScenarioRequest.academicHold("hold")
        );

        assertThat(report.decision().eligible()).isFalse();
        assertThat(report.decision().holdsClear()).isFalse();
        assertThat(report.decision().reasons()).contains("Student has an academic hold");
        assertThat(report.studentsDatabase().rowCount()).isEqualTo(3L);
        assertThat(report.coursesDatabase().rowCount()).isEqualTo(4L);
        assertThat(report.render()).contains("Decision: INELIGIBLE");
    }

    @Test
    void missingPrerequisiteMakesStudentIneligible() throws Exception {
        DatabasePerServiceSampleRunner.SampleReport report = runScenario(
                DatabasePerServiceSampleRunner.ScenarioRequest.missingPrerequisite("missing-prereq")
        );

        assertThat(report.decision().eligible()).isFalse();
        assertThat(report.decision().prerequisitesSatisfied()).isFalse();
        assertThat(report.decision().reasons()).contains("Student is missing at least one required prerequisite");
        assertThat(report.studentsDatabase().rowCount()).isEqualTo(2L);
        assertThat(report.coursesDatabase().rowCount()).isEqualTo(4L);
        assertThat(report.render()).contains("Decision: INELIGIBLE");
    }

    private DatabasePerServiceSampleRunner.SampleReport runScenario(DatabasePerServiceSampleRunner.ScenarioRequest scenario)
            throws Exception {
        int studentsPort = freePort();
        int coursesPort = freePort();

        studentsContext = new SpringApplicationBuilder(StudentsApplication.class)
                .properties(applicationProperties(studentsPort, "studentpdb", "students_app"))
                .run();

        coursesContext = new SpringApplicationBuilder(CoursesApplication.class)
                .properties(applicationProperties(coursesPort, "coursepdb", "courses_app"))
                .run();

        DatabasePerServiceSampleRunner.SampleReport report = new DatabasePerServiceSampleRunner(java.net.http.HttpClient.newHttpClient())
                .run("http://localhost:" + studentsPort, "http://localhost:" + coursesPort, scenario);
        System.out.println(report.fullReport());
        return report;
    }

    private void truncateStudentsData() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("studentpdb"), "students_app", "testpwd");
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("DELETE FROM student_completed_courses");
            statement.executeUpdate("DELETE FROM students");
            connection.commit();
        }
    }

    private void truncateCoursesData() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("coursepdb"), "courses_app", "testpwd");
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("DELETE FROM course_offerings");
            statement.executeUpdate("DELETE FROM course_prerequisites");
            statement.executeUpdate("DELETE FROM course_catalog");
            connection.commit();
        }
    }

    private static String jdbcUrl(String serviceName) {
        return "jdbc:oracle:thin:@%s:%d/%s".formatted(oracle.getHost(), oracle.getOraclePort(), serviceName);
    }

    private static String[] applicationProperties(int port, String serviceName, String username) {
        String jdbcUrl = jdbcUrl(serviceName);
        return new String[] {
                "server.port=" + port,
                "SERVER_PORT=" + port,
                "spring.datasource.url=" + jdbcUrl,
                "JDBC_URL=" + jdbcUrl,
                "spring.datasource.username=" + username,
                "USERNAME=" + username,
                "spring.datasource.password=testpwd",
                "PASSWORD=testpwd"
        };
    }

    private static void waitForDatabase(String serviceName, String username) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try (var connection = DriverManager.getConnection(jdbcUrl(serviceName), username, "testpwd")) {
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                Thread.sleep(1000);
            }
        }
        throw new IllegalStateException("Timed out waiting for database service " + serviceName, lastFailure);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
