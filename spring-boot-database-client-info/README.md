---
name: spring-boot-database-client-info
description: Spring Boot sample that sets MODULE and ACTION client info on Oracle AI Database connections.
tags:
  - Database
  - Java
  - JDBC
  - Observability
  - SpringBoot
blog_post: "https://andersswanson.dev/2025/11/03/unlocking-session-visibility-with-oracle-client-info/"
---

# Spring Boot Oracle AI Database Client Info

This example application demonstrates how to add client information to Oracle AI Database connections in a Spring Boot application. It sets client identifiers on database connections for better monitoring and tracing. The application includes a simple REST API for managing books, which sets custom client info (MODULE and ACTION) on each connection used in the API operations.

#### Important Classes

- [ClientInfoApplication](./src/main/java/com/example/clientinfo/ClientInfoApplication.java): The main Spring Boot application class that configures the DataSource with client info using a BeanPostProcessor.
- [ClientInfoDataSource](./src/main/java/com/example/clientinfo/ClientInfoDataSource.java): A wrapper around the DataSource that applies client info properties to connections.
- [BooksController](./src/main/java/com/example/clientinfo/BooksController.java): A REST controller providing CRUD operations for books, using low-level JDBC with custom client info settings (OCSID.MODULE = "Books", OCSID.ACTION = method name).

## References

- [Oracle AI Database JDBC Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/jjdbc/)
- [Spring Boot Data JDBC](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#data.sql.jdbc)
- Related samples: [Spring Boot with OJDBC Tracing](../spring-boot-jdbc-tracing/README.md)

## Prerequisites

- Java 21+, Maven
- Docker compatible environment with docker compose (note: use `docker compose` instead of `docker-compose` on newer systems)

## Setup Oracle AI Database Free with docker compose

Start the Oracle AI Database Free container with docker compose:

```bash
cd spring-boot-database-client-info
docker compose up -d
```

When the database starts, the [grant_permissions.sql](./oracle/grant_permissions.sql) is run, creating a test user and a `books` table.

## Run the sample

This command starts the Java application:

```bash
mvn spring-boot:run
```

The application will run on `http://localhost:8080`.

## Run the tests

The integration test `src/test/java/com/example/clientinfo/ClientInfoApplicationTest.java` verifies that the client info settings are applied when interacting with the database.

- Ensure Docker is running so Testcontainers can start Oracle AI Database Free.
- From the `spring-boot-database-client-info` directory, run the module tests:

  ```bash
  mvn test
  ```

- To execute only this test class:

  ```bash
  mvn -Dtest=ClientInfoApplicationTest test
  ```

## Test the API

The application provides a REST API under `/books` for managing books. Each operation sets client info on the database connection.

### Create a book (POST)

```bash
curl -X POST http://localhost:8080/books \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Book","author":"John Doe","isbn":"1234567890","publishedDate":"2023-01-01"}'
```

### Get all books (GET)

```bash
curl http://localhost:8080/books
```

### Get book by ID (GET)

```bash
curl http://localhost:8080/books/1
```

### Update book (PUT)

```bash
curl -X PUT http://localhost:8080/books/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated Book","author":"Jane Doe","isbn":"0987654321","publishedDate":"2024-01-01"}'
```

### Delete book (DELETE)

```bash
curl -X DELETE http://localhost:8080/books/1
```

## Monitoring Client Info

You can query the database (e.g., using SQL*Plus or another tool) to view active sessions and their client info:

```sql
SELECT sid, client_identifier, module, action FROM v$session WHERE username = 'TESTUSER';
```

This will show the client info set by the application, such as MODULE="Books" and ACTION corresponding to the API method (e.g., "createBook").

## Run the tests

From the `spring-boot-database-client-info` directory, run the tests with `mvn test`.

The test uses Testcontainers to spin up an Oracle AI Database Free instance, run a transaction with client info, and verify the client info in the `V$SESSION` view.