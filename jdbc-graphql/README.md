---
name: jdbc-graphql
description: Plain JDBC sample that runs SQL GraphQL queries against Oracle AI Database tables.
tags:
  - Database
  - Graph
  - GraphQL
  - Java
  - JDBC
  - JSON
blog_post: "https://andersswanson.dev/2026/01/08/graphql-oracle-instantly-query-relational-data/"
---

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

## Run the test

```bash
mvn test
```

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
