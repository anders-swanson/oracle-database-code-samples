
---
name: students-service
description: Student service for the database-per-service example.
tags:
  - Database
  - Java
  - SpringBoot
  - PDB
---

# Students Service

This module is the student-profile service from the [database-per-service sample](../README.md). It runs as an independent Spring Boot application backed by its own Oracle AI Database pluggable database, `studentpdb`.

The service owns:

- `students`
- `student_completed_courses`

The REST API exposes:

- `POST /students`
- `POST /students/{studentId}/completed-courses`
- `GET /students/{studentId}`
- `GET /students/{studentId}/completed-courses`
- `GET /db-info`

## Prerequisites

- Java 21+
- Maven 3.9+
- An Oracle AI Database Free instance with the `studentpdb` pluggable database created

The parent sample includes a setup script at [`../sample/src/test/resources/create-pdbs.sql`](../sample/src/test/resources/create-pdbs.sql) that creates `studentpdb`, the `students_app` schema, and this service's tables.

## Run the service

```bash
mvn spring-boot:run \
  -DJDBC_URL=jdbc:oracle:thin:@localhost:1521/studentpdb \
  -DUSERNAME=students_app \
  -DPASSWORD=testpwd \
  -DSERVER_PORT=8081
```

The default configuration is defined in [`src/main/resources/application.yaml`](./src/main/resources/application.yaml).
