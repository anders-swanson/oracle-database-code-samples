---
name: oracle-ai-database-docker-compose
description: Docker Compose setup for running Oracle AI Database Free locally.
tags:
  - Database
  - docker
  - oraclefree
blog_post: "https://andersswanson.dev/2025/05/22/oracle-database-for-free/"
---

# Oracle AI Database Docker Compose

This module provides a simple Docker Compose setup for running an Oracle AI Database Free container, suitable for AI vector search and other Oracle AI features demos.

## Prerequisites

- Docker and Docker Compose installed.

## Usage

1. Navigate to this directory.
2. Run `docker-compose up -d` to start the Oracle AI Database container.

The container will be available on `localhost:1521`.

## Database Details

- **Image**: gvenzl/oracle-free:23.26.1-slim-faststart
- **SYS Password**: Welcome12345
- **Default PDB**: freepdb1 (init script switches to this container)
- **Init Scripts**: Place SQL scripts in the `oracle/` directory to run on startup.

## Connecting

Use SQLcl or any Oracle client:
- Host: localhost
- Port: 1521
- SID: freepdb1
- Username: SYS (or create users as needed)
- Password: Welcome12345
