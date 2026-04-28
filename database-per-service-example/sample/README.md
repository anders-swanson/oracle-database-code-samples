
---
name: sample-runner
description: Runner and end-to-end tests for the database-per-service example.
tags:
  - Database
  - Java
  - Testcontainers
  - PDB
---

# Sample Runner

This module contains the command-line runner and end-to-end tests for the [database-per-service sample](../README.md). It composes the `students` and `courses` services over HTTP and demonstrates a small registration check without cross-PDB joins or shared schemas.

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
