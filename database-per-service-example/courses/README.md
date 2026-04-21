
# Courses Service

This module is the course-catalog service from the [database-per-service sample](../README.md). It runs as an independent Spring Boot application backed by its own Oracle AI Database pluggable database, `coursepdb`.

The service owns:

- `course_catalog`
- `course_prerequisites`
- `course_offerings`

The REST API exposes:

- `POST /courses`
- `POST /courses/{courseCode}/prerequisites`
- `POST /course-offerings`
- `GET /courses/{courseCode}`
- `GET /courses/{courseCode}/prerequisites`
- `GET /course-offerings/{courseCode}?termCode=...`
- `GET /db-info`

## Prerequisites

- Java 21+
- Maven 3.9+
- An Oracle AI Database Free instance with the `coursepdb` pluggable database created

The parent sample includes a setup script at [`../sample/src/test/resources/create-pdbs.sql`](../sample/src/test/resources/create-pdbs.sql) that creates `coursepdb`, the `courses_app` schema, and this service's tables.

## Run the service

From the repository root:

```bash
mvn -f database-per-service-example/pom.xml -pl courses spring-boot:run \
  -DJDBC_URL=jdbc:oracle:thin:@localhost:1521/coursepdb \
  -DUSERNAME=courses_app \
  -DPASSWORD=testpwd \
  -DSERVER_PORT=8082
```

The default configuration is defined in [`src/main/resources/application.yaml`](./src/main/resources/application.yaml).

## Run tests

```bash
mvn -f database-per-service-example/pom.xml -pl courses test
```
