# TypeScript Testcontainers Sample

This folder contains the reusable Testcontainers helpers for the TypeScript samples. The classes wrap Oracle AI Database Free startup and return a ready-to-use `oracledb` connection for tests or scripts.

Key files:

- [`oracle_database_container.ts`](./oracle_database_container.ts): simple container wrapper
- [`generic_oracle_database_container.ts`](./generic_oracle_database_container.ts): `GenericContainer`-based implementation with a typed started container

## Prerequisites

- Node.js
- `npm install`
- Docker compatible environment

## Run the example

From the `typescript/` directory:

```bash
npm run testcontainers-example
```

The example test starts `gvenzl/oracle-free:23.26.1-slim-faststart`, waits for the database to become ready, and connects with the application user created by the container environment variables.
