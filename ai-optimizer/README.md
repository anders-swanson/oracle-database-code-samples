---
name: ai-optimizer
description: Docker Compose setup for running the ai-optimizer service with Oracle AI Database Free locally.
tags:
  - AI
  - Database
  - docker
  - oraclefree
blog_post: "https://andersswanson.dev/2025/10/21/ai-optimizer-toolkit-set-up-a-local-sandbox/"
---

# AI Optimizer Docker Compose

This folder contains a Docker Compose setup for running the `ai-optimizer` service alongside Oracle AI Database Free.

The compose file starts:

- `ai-optimizer` on `localhost:8501`
- Oracle AI Database Free on `localhost:1522`

The initialization script in [`oracle/grant_permissions.sql`](./oracle/grant_permissions.sql) creates:

- user `testuser`
- password `testpwd`

## Prerequisites

- Docker compatible environment
- An available `ai-optimizer-aio` image, optionally selected with the `TAG` environment variable

## Run the stack

From this directory:

```bash
docker compose up -d
```

The Oracle AI Database container uses `Welcome12345` as the admin password and mounts `oracle/` as the startup script directory.
