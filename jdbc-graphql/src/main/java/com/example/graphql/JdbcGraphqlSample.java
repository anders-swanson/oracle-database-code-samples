package com.example.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import oracle.sql.json.OracleJsonArray;
import oracle.sql.json.OracleJsonObject;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

public final class JdbcGraphqlSample {
    private static final String SCHEMA_RESOURCE = "/schema.sql";
    private static final String SAMPLE_DATA_RESOURCE = "/sample-data.csv";

    private static final String INSERT_STUDENT_SQL = """
            insert into students (student_id, first_name, last_name, email)
            values (?, ?, ?, ?)
            """;
    private static final String INSERT_COURSE_SQL = """
            insert into courses (course_id, code, title)
            values (?, ?, ?)
            """;
    private static final String INSERT_ENROLLMENT_SQL = """
            insert into enrollments (student_id, course_id, grade)
            values (?, ?, ?)
            """;

    private static final String STUDENT_DOCUMENT_GRAPHQL = """
            select data from graphql('students(first_name: $value) {
                id: student_id
                firstName: first_name
                lastName: last_name
                email
                enrollments @link(to: [STUDENT_ID]) {
                    studentId: student_id
                    courseId: course_id
                    enrolledOn: enrolled_on
                    grade
                    courses @link(from: [COURSE_ID]) {
                        id: course_id
                        code
                        title
                    }
                }
            }' passing ? as "value")
            """;
    private static final String COURSE_DOCUMENT_GRAPHQL = """
            select data from graphql('courses(code: $value) {
                id: course_id
                code
                title
                enrollments @link(to: [COURSE_ID]) {
                    grade
                    students @link(from: [STUDENT_ID]) {
                        id: student_id
                        firstName: first_name
                        lastName: last_name
                        email
                    }
                }
            }' passing ? as "value")
            """;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        OracleJsonObject alice;
        OracleJsonObject math201;
        try (Connection connection = createDataSource(args[0], args[1], args[2]).getConnection()) {
            applySchema(connection, SCHEMA_RESOURCE);
            loadSampleData(connection);

            alice = queryDocument(connection, STUDENT_DOCUMENT_GRAPHQL, "Alice");
            math201 = queryDocument(connection, COURSE_DOCUMENT_GRAPHQL, "MATH201");
        }

        validateExpectedResults(alice, math201);

        System.out.println("Student GraphQL document:");
        System.out.println(alice);
        System.out.println();
        System.out.println("Course GraphQL document:");
        System.out.println(math201);
        System.out.println();
        System.out.printf(
                Locale.US,
                "Fetched %d enrollment(s) for Alice and %d roster row(s) for MATH201 with SQL GRAPHQL().%n",
                alice.get("enrollments").asJsonArray().size(),
                math201.get("enrollments").asJsonArray().size()
        );
    }

    static PoolDataSource createDataSource(String url, String username, String password) throws SQLException {
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("GRAPHQL_SAMPLE_" + ManagementFactory.getRuntimeMXBean().getPid());
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setURL(url);
        return dataSource;
    }

    static void applySchema(Connection connection, String resourcePath) throws IOException, SQLException {
        String script = readResource(resourcePath);
        for (String rawStatement : script.split("(?m)^/\\s*$")) {
            String ddl = rawStatement.trim();
            if (ddl.isEmpty()) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(ddl);
            }
        }
    }

    static void loadSampleData(Connection connection) throws IOException, SQLException {
        List<EnrollmentRow> rows = readSampleRows(JdbcGraphqlSample.SAMPLE_DATA_RESOURCE);
        Map<Integer, StudentRow> students = new LinkedHashMap<>();
        Map<Integer, CourseRow> courses = new LinkedHashMap<>();

        for (EnrollmentRow row : rows) {
            students.putIfAbsent(row.studentId(), new StudentRow(
                    row.studentId(),
                    row.firstName(),
                    row.lastName(),
                    row.email()
            ));
            courses.putIfAbsent(row.courseId(), new CourseRow(
                    row.courseId(),
                    row.courseCode(),
                    row.courseTitle()
            ));
        }

        try (PreparedStatement studentStatement = connection.prepareStatement(INSERT_STUDENT_SQL);
             PreparedStatement courseStatement = connection.prepareStatement(INSERT_COURSE_SQL);
             PreparedStatement enrollmentStatement = connection.prepareStatement(INSERT_ENROLLMENT_SQL)) {
            for (StudentRow student : students.values()) {
                studentStatement.setInt(1, student.studentId());
                studentStatement.setString(2, student.firstName());
                studentStatement.setString(3, student.lastName());
                studentStatement.setString(4, student.email());
                studentStatement.addBatch();
            }
            studentStatement.executeBatch();

            for (CourseRow course : courses.values()) {
                courseStatement.setInt(1, course.courseId());
                courseStatement.setString(2, course.code());
                courseStatement.setString(3, course.title());
                courseStatement.addBatch();
            }
            courseStatement.executeBatch();

            for (EnrollmentRow row : rows) {
                enrollmentStatement.setInt(1, row.studentId());
                enrollmentStatement.setInt(2, row.courseId());
                enrollmentStatement.setString(3, row.grade());
                enrollmentStatement.addBatch();
            }
            enrollmentStatement.executeBatch();
        }
    }

    static OracleJsonObject queryDocument(Connection connection, String graphqlQuery, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(graphqlQuery)) {
            statement.setString(1, value);
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                throw new IllegalStateException("GRAPHQL query returned no rows");
            }
            return resultSet.getObject("data", OracleJsonObject.class);
        }
    }

    static void validateExpectedResults(OracleJsonObject alice, OracleJsonObject math201) {
        if (!"Alice".equals(alice.getString("firstName"))) {
            throw new IllegalStateException("Expected firstName=Alice but found " + alice.getString("firstName"));
        }
        if (!"Smith".equals(alice.getString("lastName"))) {
            throw new IllegalStateException("Expected lastName=Smith but found " + alice.getString("lastName"));
        }
        OracleJsonArray aliceEnrollments = alice.get("enrollments").asJsonArray();
        if (aliceEnrollments.size() != 2) {
            throw new IllegalStateException("Expected Alice to have 2 enrollments but found " + aliceEnrollments.size());
        }
        String aliceCourses = aliceEnrollments.stream()
                .map(value -> value.asJsonObject().get("courses").asJsonObject().getString("code"))
                .sorted()
                .toList()
                .toString();
        if (!"[CS101, MATH201]".equals(aliceCourses)) {
            throw new IllegalStateException("Expected Alice course codes [CS101, MATH201] but found " + aliceCourses);
        }

        if (!"MATH201".equals(math201.getString("code"))) {
            throw new IllegalStateException("Expected course code MATH201 but found " + math201.getString("code"));
        }
        OracleJsonArray math201Enrollments = math201.get("enrollments").asJsonArray();
        if (math201Enrollments.size() != 2) {
            throw new IllegalStateException("Expected MATH201 to have 2 enrollments but found " + math201Enrollments.size());
        }
        String math201Students = math201Enrollments.stream()
                .map(value -> value.asJsonObject().get("students").asJsonObject().getString("firstName"))
                .sorted()
                .toList()
                .toString();
        if (!"[Alice, Bob]".equals(math201Students)) {
            throw new IllegalStateException("Expected MATH201 roster [Alice, Bob] but found " + math201Students);
        }
    }

    static List<EnrollmentRow> readSampleRows(String resourcePath) throws IOException {
        List<EnrollmentRow> rows = new ArrayList<>();
        String[] lines = readResource(resourcePath).split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            rows.add(new EnrollmentRow(
                    Integer.parseInt(columns[0]),
                    columns[1],
                    columns[2],
                    columns[3],
                    Integer.parseInt(columns[4]),
                    columns[5],
                    columns[6],
                    columns[7]
            ));
        }
        return rows;
    }

    static String readResource(String resourcePath) throws IOException {
        try (InputStream stream = JdbcGraphqlSample.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record StudentRow(int studentId, String firstName, String lastName, String email) {
    }

    record CourseRow(int courseId, String code, String title) {
    }

    record EnrollmentRow(
            int studentId,
            String firstName,
            String lastName,
            String email,
            int courseId,
            String courseCode,
            String courseTitle,
            String grade
    ) {
    }
}
