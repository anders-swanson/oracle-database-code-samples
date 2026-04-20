# Database Per Service with Oracle AI Database Pluggable Databases

This sample demonstrates the database-per-service pattern on Oracle AI Database with a realistic cross-service workflow: **registration eligibility**.

Each service owns its own pluggable database (PDB), schema, tables, and datasource:

- `students/`: student profile and completed-course history in `studentpdb`
- `courses/`: course catalog, prerequisites, and term offerings in `coursepdb`
- `sample/`: command-line runner and end-to-end test that compose data from both services over HTTP

The sample intentionally avoids cross-PDB joins, shared schemas, and database links. Eligibility is computed at the application layer after fetching service-owned data from each boundary.

## Module structure

### `students/`

Owns:

- `students`
- `student_completed_courses`

Exposes:

- `POST /students`
- `POST /students/{studentId}/completed-courses`
- `GET /students/{studentId}`
- `GET /students/{studentId}/completed-courses`
- `GET /db-info`

### `courses/`

Owns:

- `course_catalog`
- `course_prerequisites`
- `course_offerings`

Exposes:

- `POST /courses`
- `POST /courses/{courseCode}/prerequisites`
- `POST /course-offerings`
- `GET /courses/{courseCode}`
- `GET /courses/{courseCode}/prerequisites`
- `GET /course-offerings/{courseCode}?termCode=...`
- `GET /db-info`

### `sample/`

Contains:

- `com.example.sample.DatabasePerServiceSampleRunner`
- the Testcontainers-backed end-to-end test
- the SQL*Plus script that provisions the PDBs and schema objects

## What the sample proves

- Oracle AI Database can isolate service-owned data at the PDB boundary
- two Spring Boot services can use different PDB service names in the same Oracle AI Database instance
- a realistic business decision can be composed without violating database ownership

The composed workflow is:

1. Create a student profile in the `students` service
2. Add completed-course history in the `students` service
3. Create catalog and offering data in the `courses` service
4. Read both service-owned datasets
5. Compute registration eligibility in the sample runner

The decision uses:

- student status
- academic hold flag
- prerequisite completion
- offering availability
- seat availability

## Local run flow

1. Start an Oracle AI Database Free container.
2. Run the setup script in [`sample/src/test/resources/create-pdbs.sql`](./sample/src/test/resources/create-pdbs.sql) as `sysdba`.
3. Start the `students` service.
4. Start the `courses` service.
5. Run the sample runner.

The setup script creates:

- `studentpdb`
- `coursepdb`
- `students_app`
- `courses_app`
- the `students` service tables
- the `courses` service tables

## Run the services

Start the `students` service:

```bash
mvn -f database-per-service-example/pom.xml -pl students spring-boot:run \
  -DJDBC_URL=jdbc:oracle:thin:@localhost:1521/studentpdb \
  -DUSERNAME=students_app \
  -DPASSWORD=testpwd \
  -DSERVER_PORT=8081
```

Start the `courses` service:

```bash
mvn -f database-per-service-example/pom.xml -pl courses spring-boot:run \
  -DJDBC_URL=jdbc:oracle:thin:@localhost:1521/coursepdb \
  -DUSERNAME=courses_app \
  -DPASSWORD=testpwd \
  -DSERVER_PORT=8082
```

Run the sample runner:

```bash
mvn -f database-per-service-example/pom.xml -pl sample exec:java \
  -DSTUDENTS_BASE_URL=http://localhost:8081 \
  -DCOURSES_BASE_URL=http://localhost:8082
```

The runner seeds an eligibility scenario, reads both services back, and prints a report showing the decision plus proof that the services are connected to different PDBs.

## Automated tests

Run the module test suite with:

```bash
mvn -f database-per-service-example/pom.xml clean test
```

The end-to-end tests in `sample/`:

- start Oracle AI Database Free with Testcontainers
- provision `studentpdb` and `coursepdb`
- boot both Spring Boot applications against separate PDBs
- seed data through service APIs
- verify eligible and ineligible scenarios
- assert both the business result and the PDB ownership boundaries

Because the tests create PDBs inside the Oracle container, repeated runs require enough local Docker disk space.
