---
name: spring-cloud-config
description: Spring Cloud Config sample that stores configuration properties in Oracle AI Database.
tags:
  - Database
  - Java
  - microservices
  - SpringBoot
blog_post: "https://andersswanson.dev/2025/11/21/spring-cloud-config-jdbc-backend-oracle-ai-database/"
---

# Spring Cloud Config with Oracle Database

This module demonstrates how to use Oracle Database as the backend for a Spring Cloud Config Server. It includes a config server that stores configuration properties in an Oracle Database via JDBC, a simple client application that consumes these configurations, and a Docker Compose setup for the database.

## Prerequisites

- Java 21 or later
- Maven
- Docker (for running the Oracle Database container)

## Directory Structure

- **client/**: The Spring Boot client application that fetches configurations from the server.
- **server/**: The Spring Cloud Config Server application with JDBC backend.
- **oracle/**: Initialization scripts for the database (e.g., grant_permissions.sql to create user and PROPERTIES table).
- **docker-compose.yml**: Docker Compose file to spin up an Oracle Database instance.
- **crud-properties.md**: Additional documentation on using the CRUD API for managing properties.

## Setup

1. **Start the Oracle Database**:
   Run the Docker Compose file to start the Oracle Free container:
   ```
   docker-compose up -d
   ```
   This sets up the database on `localhost:1527` with PDB `freepdb1`. Admin password is `Welcome12345`. The init script in `oracle/grant_permissions.sql` creates the `testuser` user (password: `testpwd`) and the `PROPERTIES` table for Spring Cloud Config.

2. **Build and Run the Config Server**:
   Navigate to the `server` directory and run:
   ```
   mvn clean compile spring-boot:run
   ```
   The server runs on `http://localhost:8888` (configured in `server/src/main/resources/application.yaml`). It uses JDBC to connect to the database at `jdbc:oracle:thin:@localhost:1527/freepdb1` with credentials `testuser/testpwd`.

3. **Insert Configuration Properties**:
   Use SQL or the optional CRUD API (exposed at `/api/properties`) to add properties to the `PROPERTIES` table. Example row:
   - ID: 1
   - APPLICATION: 'myapp'
   - PROFILE: 'dev'
   - LABEL: 'latest'
   - PROP_KEY: 'config.key'
   - VALUE: 'config-value'

   The server's custom SQL query is: `SELECT PROP_KEY, VALUE from PROPERTIES where APPLICATION=? and PROFILE=? and LABEL=?`.

```bash
curl -X POST http://localhost:8888/api/properties \
     -H "Content-Type: application/json" \
     -d '{
           "application": "myapp",
           "profile": "dev",
           "label": "latest",
           "propKey": "config.key",
           "value": "config-value"
         }'
```

4. **Build and Run the Client**:
   Navigate to the `client` directory and run:
   ```
   mvn clean compile spring-boot:run
   ```
   The client is configured (in `client/src/main/resources/application.yaml`) to fetch from `http://localhost:8888` with application name `myapp` and active profile `dev`. It injects `${config.key}` in `Controller.java`.

## Usage

- **Test Config Fetch**:
  Once the client is running (default port 8080), access:
  ```
  curl http://localhost:8080/value
  ```
  This should return the value from the config server, e.g., "This is my config value: config-value".

- **Manage Properties via CRUD API** (Optional):
  The `PropertiesController` in the server provides REST endpoints at `/api/properties` for CRUD operations. See `crud-properties.md` for curl examples.

## Troubleshooting

- Ensure the database container is healthy (check `docker logs configdb`).
- Verify properties in the database match the client's application, profile, and label.
- Check server logs for JDBC connection errors.
- If properties aren't resolving, test the config endpoint directly: `curl http://localhost:8888/myapp/dev/latest`.

For more details, see the Spring Cloud Config documentation: https://docs.spring.io/spring-cloud-config/docs/current/reference/html/.
