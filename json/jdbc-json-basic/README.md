---
name: json/jdbc-json-basic
description: Plain JDBC sample for inserting, querying, updating, and deleting Oracle JSON data.
tags:
  - Database
  - Java
  - JDBC
  - JSON
  - Testcontainers
blog_post: "https://andersswanson.dev/2026/02/24/hands-on-crud-with-jdbc-and-json-relational-duality-views/"
---

# JDBC JSON Data Type Sample

This sample demonstrates how to interact with the Oracle JSON data type using plain JDBC. A console-style program inserts, queries, updates, and deletes JSON documents stored in a relational table while leveraging SQL/JSON operators like `json_value` and `json_transform`.

## Highlights
- Bind JSON payloads as `VARCHAR2`/SQL JSON and hydrate them back into Jakarta JSON `JsonObject` instances.
- Filter documents by attributes with `json_value` and apply in-place updates via `json_transform`.
- Exercise the complete insert/read/update/delete lifecycle against a JSON column backed by Oracle Database Free.
- Validate the flow with a Testcontainers-powered integration test that provisions a fresh database for each run.

## Prerequisites
- Java 21+
- Maven 3.9+
- Docker Desktop or another OCI-compatible container runtime (required for the Testcontainers test)

## Run the sample

From the repository root:

```bash
mvn compile exec:java -Dexec.args="jdbc:oracle:thin:@localhost:1521/freepdb1 testuser testpwd"
```

The program will insert a JSON document describing a product, fetch it back, update its price, query similar products by category, and delete the record.

## Run the tests

```bash
docker pull gvenzl/oracle-free:23.26.1-slim-faststart
mvn test
```

`JsonAttributesSampleTest` starts Oracle Database Free in a container, runs the CRUD scenario, and tears down the database afterwards.

## Learn more
- [JSON Data Type in Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/adjsn/json-data-type.html)
- [SQL/JSON Functions](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/SQL-JSON.html)
