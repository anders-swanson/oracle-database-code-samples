---
name: ords-docker-compose
description: Docker Compose setup for running ORDS with Oracle AI Database Free locally.
tags:
  - Database
  - docker
  - MongoDB
  - ORDS
  - oraclefree
blog_post: "https://andersswanson.dev/2025/12/12/oracle-rest-data-services-ords-with-docker-compose/"
---

# Oracle REST Data Services (ORDS) + Oracle AI Database with Docker Compose

Simple Docker Compose setup to run Oracle REST Data Services (ORDS) connected to an Oracle AI Database Free container!

ORDS enables a variety of HTTP APIs for the database, including the wire-compatible MongoDB API, APEX, SQL Worksheet, and more.

## Containers

- **ords**: ORDS container for exposing REST APIs. The ORDS HTTP dashboard is available on `http://localhost:8888`
- **ordsdb**: Oracle AI Database container. The database container externally accessible at `testuser/testpwd@localhost:1555/freepdb1`

The setup includes pre-configured volumes for ORDS configuration and database initialization scripts.

## Prerequisites

- Docker-compatible container runtime and Docker Compose installed on your system.

## Directory Structure

- `docker-compose.yml`: Main composition file defining services.
- `oracle/`: Database initialization scripts (e.g.,[ `ords_init.sql`](./oracle/ords_init.sql)).
- `ords_config/`: Placeholder ORDS configuration directory. You do not need to modify this directory, ORDS will populate its configuration data here on startup.

## Usage

1. Clone the repo and navigate to this directory:
   ```
   cd ords-docker-compose
   ```

2. Start the services:
   ```
   docker-compose up -d
   ```

3. Stop the services:
   ```
   docker-compose down
   ```

## Accessing Services

- **ORDS HTTP Endpoint**: `http://localhost:8888/`. Use `testuser/testpwd` as the login information for services like SQL Developer.
- **MongoDB API**: Port 27017 (e.g., connect using MongoDB clients to `localhost:27017`).
- **Oracle AI Database**: Connect via SQL*Plus or tools like SQLcl to `localhost:1555/freepdb1` with username `testuser` and password `testpwd`.

## Configuration Details

- **Features Enabled**:
  - REST-Enabled SQL and Database APIs
  - SQL Developer web view
  - Wire compatible MongoDB API
- **Security**: Uses HTTP for local demo mode. If you're using ORDS in production, ensure you use proper certificates.

## Customization

- Add database initialization scripts to `oracle/` (e.g., modify `ords_init.sql` or add your own scripts).
- Environment variables can be adjusted in `docker-compose.yml` (e.g., change passwords or ports).

## Notes

- The database uses the `gvenzl/oracle-free:23.26.1-slim-faststart` image.
- ORDS uses `container-registry.oracle.com/database/ords:latest`.
- Volumes persist configuration; remove them with `docker-compose down -v` if needed.
- For more details on ORDS, visit the [official documentation](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/).

If you encounter issues, check container logs:
```
docker logs ords
docker logs ordsdb
