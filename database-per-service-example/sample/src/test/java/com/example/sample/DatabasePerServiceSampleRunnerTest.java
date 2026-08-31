package com.example.sample;

import java.net.ServerSocket;
import java.sql.DriverManager;
import java.time.Duration;

import com.example.courses.CoursesApplication;
import com.example.students.StudentsApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DatabasePerServiceSampleRunnerTest {
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(10))
            .withUsername("testuser")
            .withPassword("testpwd");

    @BeforeAll
    static void setUp() throws Exception {
        oracle.start();

        // Mount the PDB schema and run it on the Testcontainers database
        oracle.copyFileToContainer(
                MountableFile.forClasspathResource("create-pdbs.sql"),
                "/tmp/create-pdbs.sql"
        );
        var result = oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/create-pdbs.sql");
        assertThat(result.getExitCode())
                .withFailMessage("PDB setup failed.%nSTDOUT:%n%s%nSTDERR:%n%s", result.getStdout(), result.getStderr())
                .isZero();

        // Wait for the studentpdb and coursepdb to be accessible (can get a database connection)
        waitForDatabase("studentpdb", "students_app");
        waitForDatabase("coursepdb", "courses_app");
    }

    @Test
    void mainRunsAllScenariosAcrossTwoServices() throws Exception {
        int studentsPort = freePort();
        int coursesPort = freePort();

        // Start the StudentsApplication
        new SpringApplicationBuilder(StudentsApplication.class)
                .properties(applicationProperties(studentsPort, "studentpdb", "students_app"))
                .run();

        // Start the CoursesApplication
        new SpringApplicationBuilder(CoursesApplication.class)
                .properties(applicationProperties(coursesPort, "coursepdb", "courses_app"))
                .run();

        // Run the sample against the test context
        DatabasePerServiceSampleRunner.main(new String[] {
                "http://localhost:" + studentsPort,
                "http://localhost:" + coursesPort
        });
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
