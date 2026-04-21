---
name: golang/connection
description: Shared Go connection helper for local Oracle AI Database access with godror.
tags:
  - Database
  - Go
blog_post: ""
---

# Go Database Connection Sample

This folder contains the shared Go connection helper used by the samples in [`../README.md`](../README.md). The implementation wraps `godror` connection setup, pooling, and optional `TNS_ADMIN` handling for Oracle AI Database.

The main entry point is [`database_connection.go`](./database_connection.go), which provides:

- `DefaultLocalhostConnection()` for `localhost:1521/freepdb1`
- `NewDatabase(username, password, url)` for custom connection details

## Prerequisites

- Go
- Oracle client libraries available through `DYLD_LIBRARY_PATH`
- An Oracle AI Database instance reachable from your machine

## Usage

Import the package from another Go program:

```go
import "github.com/anders-swanson/oracle-database-java-samples/golang/connection"
```

Then create a database handle:

```go
db := connection.DefaultLocalhostConnection()
```
