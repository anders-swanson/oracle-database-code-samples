# JDBC GraphQL

This module demonstrates GraphQL over relational tables from a plain Java/JDBC application on Oracle AI Database.

The sample uses a small many-to-many schema:

- `STUDENTS`
- `COURSES`
- `ENROLLMENTS`

Then it runs SQL `GRAPHQL(...)` queries that return nested JSON documents:

- fetch one student with enrolled courses
- fetch one course with its student roster
- load seed data from a CSV resource and insert it with JDBC batches
- keep the GraphQL documents as constants and pass scalar variable values into the SQL `GRAPHQL(...)` call
- keep all joins server-side while the JDBC client receives shaped JSON

The shape is intentionally close to the January 2026 blog posts about GraphQL over Oracle AI Database relational data and ORDS GraphQL, but this module stays focused on the direct JDBC + SQL path.

## Why this sample fits the repo

This follows the same pattern as the other top-level JDBC samples in this repository:

- one focused Maven module
- one main entrypoint under `com.example`
- a small relational schema created at runtime
- a JUnit test in `src/test/java`
- a README that explains how to run it locally

## Run the test

```bash
mvn test
```

The test follows the same pattern as the other top-level JDBC samples: it starts an Oracle AI Database Free container with Testcontainers and runs the sample end to end by calling `main(...)`. The sample itself validates that Alice returns two enrollments and that `MATH201` returns the expected two-student roster. If the container image resolves to a pre-26ai Oracle Free build, the test skips because SQL `GRAPHQL(...)` is a 26ai feature.

## Run the sample app

Against an Oracle AI Database 26ai instance:

```bash
mvn compile exec:java -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

You should see two JSON documents printed:

- one for `Alice` and her course enrollments
- one for `MATH201` and the enrolled students

## Sample GraphQL shape

The student query uses a GraphQL variable and `@link` to traverse the relational model:

```graphql
students(first_name: $value) {
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
}
```

## Notes

- Use Oracle AI Database 26ai or later for this sample.
- If you want to expose a similar schema over HTTP instead of direct JDBC, the ORDS GraphQL flow is the natural follow-on sample.
