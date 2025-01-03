# Oracle Database Testcontainers

This module provides examples using [Testcontainers](https://java.testcontainers.org/) with [Oracle Database Free](https://www.oracle.com/database/free/) to test your Oracle Database Java applications using dispoable containers.

The `gvenzl/oracle-free` Oracle Database container images are recommended for use with Testcontainers and Java. The examples in this module use the Oracle Database 23ai Free image `gvenzl/oracle-free:23.5-slim-faststart`.

### [GetDatabaseConnectionTest](./src/test/java/com/example/GetDatabaseConnectionTest.java)

This test implements a basic Oracle Database test with Testcontainers. The version of the containerized database is queried to verify the test connection works.

### [InitializedDatabaseTest](./src/test/java/com/example/InitializedDatabaseTest.java)

This test demonstrates how to run an initialization script in the containerized database to configure a table schema and insert test data.

### [SpringBootDatabaseTest](./src/test/java/com/example/SpringBootDatabaseTest.java)

This test uses a containerized database as a Spring Boot datasource within the context of a `@SpringBootTest`, initializing the Spring Boot datasource properties at test startup.

The pattern shown allows developers to test their Spring Boot applications with Oracle Database using a fully featured Spring context. 

### [SysdbaInitTest](./src/test/java/com/example/SysdbaInitTest.java)

This test demonstrates how to mount a SQL script on a containerized database and run that script as `sysdba` before the test suite begins. This pattern is useful for DBA-level setup before the test, like applying user grants or creating PDBs.

The SysdbaInitTest setup script applies grants to a test user to manage Oracle Database Transacational Event Queues (JMS), and then creates a JMS queue.

### [reusable package](./src/test/java/com/example/reusable/README.md)

This package provides sample tests with a reusable Oracle Database container, allowing you to reduce startup time between database test suites.

