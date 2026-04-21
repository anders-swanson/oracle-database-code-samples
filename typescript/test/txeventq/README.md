# TypeScript TxEventQ Test

This folder contains the Vitest integration test for the TypeScript TxEventQ sample. It starts an Oracle AI Database Free container, initializes the required PL/SQL objects, produces a JSON event with `SQLProducer`, and consumes it back with `SQLConsumer`.

Key files:

- [`txeventq.test.ts`](./txeventq.test.ts): end-to-end TxEventQ test
- [`init.sql`](./init.sql): database initialization script run as `sysdba`

## Prerequisites

- Node.js
- `npm install`
- Docker compatible environment

## Run the test

From the `typescript/` directory:

```bash
npm run sql-producer-consumer
```

This runs the `plsql pub-sub` Vitest case, which starts Oracle AI Database Free with Testcontainers, applies the initialization script, produces a JSON event, and verifies the same event is consumed.
