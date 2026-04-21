---
name: json/jdbc-json-analytics
description: Plain JDBC sample that analyzes nested JSON documents with advanced SQL/JSON operators.
tags:
  - Database
  - Java
  - JDBC
  - JSON
  - Testcontainers
blog_post: ""
---

# JDBC JSON Analytics Sample

This sample explores advanced Oracle SQL/JSON operators with plain JDBC. It stores nested order documents and runs analytical queries using `JSON_TABLE`, `JSON_EXISTS`, and `JSON_ARRAYAGG` to produce insights without shredding JSON data into relational tables.

## Highlights
- Use `JSON_TABLE` to project order line items into a relational rowset directly from JSON documents.
- Aggregate product demand by combining `JSON_TABLE`, `GROUP BY`, and ordering to find the top-selling SKUs.
- Filter orders using `JSON_EXISTS` predicates and compact the result into JSON arrays via `JSON_ARRAYAGG`.
- Package the workflow as a console program and validate it with a Testcontainers-powered integration test.

## Prerequisites
- Java 21+
- Maven 3.9+
- Docker Desktop or another OCI-compatible container runtime (required for the Testcontainers test)

## Run the sample

From the repository root:

```bash
mvn compile exec:java -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

The program applies the schema from `schema.sql`, seeds sample orders, prints top products by quantity, and groups orders by shipping region while only considering items above a configurable quantity.

## Run the tests

```bash
docker pull gvenzl/oracle-free:23.26.1-slim-faststart
mvn test
```

`OrderAnalyticsSampleTest` provisions Oracle Database Free in a container, loads the schema, executes the analytics flow, and cleans up automatically.

## Learn more
- [SQL/JSON Functions](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/SQL-JSON.html)
- [Using JSON_TABLE for Analytics](https://docs.oracle.com/en/database/oracle/oracle-database/26/adjsn/json_table.html)
- [JSON_EXISTS Predicate](https://docs.oracle.com/en/database/oracle/oracle-database/26/adjsn/json_exists.html)
