
# Sample Runner

This module contains the command-line runner and end-to-end tests for the [database-per-service sample](../README.md). It composes the `students` and `courses` services over HTTP and demonstrates how to evaluate registration eligibility without cross-PDB joins or shared schemas.

Key contents:

- `com.example.sample.DatabasePerServiceSampleRunner`
- Testcontainers-backed end-to-end tests
- The PDB provisioning script at [`src/test/resources/create-pdbs.sql`](./src/test/resources/create-pdbs.sql)

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker compatible environment for the automated tests

To run the runner manually, start the `students` service on port `8081` and the `courses` service on port `8082` first.

## Run the sample runner

From the repository root:

```bash
mvn -f database-per-service-example/pom.xml -pl sample exec:java \
  -DSTUDENTS_BASE_URL=http://localhost:8081 \
  -DCOURSES_BASE_URL=http://localhost:8082
```

## Run tests

```bash
mvn -f database-per-service-example/pom.xml -pl sample test
```

The end-to-end tests provision `studentpdb` and `coursepdb` inside an Oracle AI Database Free container, boot both Spring Boot applications, seed data through the service APIs, and verify eligible and ineligible scenarios.
