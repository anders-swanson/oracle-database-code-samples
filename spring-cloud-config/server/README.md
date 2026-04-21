# Spring Cloud Config Server

This module is the server application from the [Spring Cloud Config sample](../README.md). It runs a Spring Cloud Config Server backed by Oracle AI Database over JDBC and includes an optional CRUD API for managing rows in the `PROPERTIES` table.

The server listens on port `8888` and queries configuration with:

```sql
SELECT PROP_KEY, VALUE
FROM PROPERTIES
WHERE APPLICATION = ? AND PROFILE = ? AND LABEL = ?
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker compatible environment

Start the sample database from [`../docker-compose.yml`](../docker-compose.yml), which creates the `testuser` schema and `PROPERTIES` table:

```bash
docker compose up -d
```

## Run the server

From this directory:

```bash
mvn clean compile spring-boot:run
```

The default connection settings are defined in [`src/main/resources/application.yaml`](./src/main/resources/application.yaml).

## Optional CRUD API

The server exposes `/api/properties` endpoints for listing, creating, updating, and deleting configuration rows. See [`../crud-properties.md`](../crud-properties.md) for example requests.
