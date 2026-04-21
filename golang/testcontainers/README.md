# Go Testcontainers Sample

This folder contains a Go helper for starting Oracle AI Database Free with `testcontainers-go` and a test that verifies the container can be started and queried.

Key files:

- [`oracle_container.go`](./oracle_container.go): reusable `OracleContainer` wrapper
- [`oracle_container_test.go`](./oracle_container_test.go): integration test that starts the container and queries `v$version`

## Prerequisites

- Go
- Docker compatible environment
- Oracle client libraries available through `DYLD_LIBRARY_PATH`

## Run the test

From the `golang/` directory:

```bash
go test ./testcontainers
```

The test starts `gvenzl/oracle-free:23.26.1-slim-faststart`, connects as the application user, and verifies the database banner returned from `v$version`.
