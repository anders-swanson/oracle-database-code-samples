---
name: testcontainers
description: Java Testcontainers samples for running Oracle AI Database Free in integration tests.
tags:
  - Database
  - Java
  - Testcontainers
  - oraclefree
blog_post: "https://andersswanson.dev/2025/05/29/easily-test-oracle-database-applications-with-testcontainers/"
---

# Oracle AI Database Testcontainers

This module provides examples using [Testcontainers](https://java.testcontainers.org/) with [Oracle AI Database Free](https://www.oracle.com/database/free/) to test your Oracle AI Database Java applications using dispoable containers.

The `gvenzl/oracle-free` Oracle AI Database container images are recommended for use with Testcontainers and Java. The examples in this module use the Oracle AI Database 26ai Free image `gvenzl/oracle-free:23.26.2-slim-faststart`.

## Related Blog Posts

- [Learn Testcontainers Java with Oracle AI Database Free](https://andersswanson.dev/2025/09/11/learn-testcontainers-java-with-oracle-database-free/)

### [GetDatabaseConnectionTest](./src/test/java/com/example/GetDatabaseConnectionTest.java)

This test implements a basic Oracle AI Database test with Testcontainers. The version of the containerized database is queried to verify the test connection works.

### [InitializedDatabaseTest](./src/test/java/com/example/InitializedDatabaseTest.java)

This test demonstrates how to run an initialization script in the containerized database to configure a table schema and insert test data.

### [SpringBootDatabaseTest](./src/test/java/com/example/SpringBootDatabaseTest.java)

This test uses a containerized database as a Spring Boot datasource within the context of a `@SpringBootTest`, initializing the Spring Boot datasource properties at test startup.

The pattern shown allows developers to test their Spring Boot applications with Oracle AI Database using a fully featured Spring context. 

### [SysdbaInitTest](./src/test/java/com/example/SysdbaInitTest.java)

This test demonstrates how to mount a SQL script on a containerized database and run that script as `sysdba` before the test suite begins. This pattern is useful for DBA-level setup before the test, like applying user grants or creating PDBs.

The SysdbaInitTest setup script applies grants to a test user to manage Oracle AI Database Transacational Event Queues (JMS), and then creates a JMS queue.

## [SelectAILocalTest](./src/test/java/com/example/SelectAILocalTest.java)

This test configures a local Oracle AI Database Free container to use the [DBMS_CLOUD family PL/SQL packages](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/dbms-cloud-ai-package.html), enabling the [Select AI](https://www.oracle.com/autonomous-database/select-ai/) feature in local tests.

The test uses the OCI GenAI service, and requires an OCI identity configured in `~/.oci`, as well as the `OCI_COMPARTMENT_ID` environment variable set.

### [reusable package](./src/test/java/com/example/reusable/README.md)

This package provides sample tests with a reusable Oracle AI Database container, allowing you to reduce startup time between database test suites.
