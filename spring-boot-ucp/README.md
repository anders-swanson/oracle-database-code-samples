---
name: spring-boot-ucp
description: Spring Boot sample showing Oracle Universal Connection Pool configuration for Oracle AI Database.
tags:
  - Database
  - Java
  - JDBC
  - SpringBoot
  - Testcontainers
blog_post: ""
---

# Spring Boot UCP Profiles

This sample shows teachable Oracle Universal Connection Pool (UCP) configurations for Spring Boot applications that connect to Oracle AI Database. Each configuration is isolated in a Spring profile so you can compare pool sizing, timeout, validation, harvesting, statement cache, connection labeling, pool metrics, diagnostics, and Database Resident Connection Pooling (DRCP) settings without changing Java code.

The configuration sets `spring.datasource.type` to `oracle.ucp.jdbc.PoolDataSource`, which selects Spring Boot's Oracle UCP auto-configuration. Spring Boot creates the concrete `oracle.ucp.jdbc.PoolDataSourceImpl` and binds the `spring.datasource.oracleucp.*` settings to it.

## Important Files

- [UcpApplication](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/UcpApplication.java): The Spring Boot entry point.
- [PoolReport](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/PoolReport.java): Captures the active UCP configuration from the `PoolDataSource`.
- [PoolMetrics](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/PoolMetrics.java): Captures runtime UCP pool statistics such as total, borrowed, available, pending, wait, and cumulative borrow/return counts.
- [PoolDiagnostics](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/PoolDiagnostics.java): Captures UCP diagnostic settings, JMX status, metric update interval, and registered pool names.
- [PoolReporter](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/PoolReporter.java): Logs configuration, metrics, and diagnostics at startup.
- [DynamicPoolResizingService](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/DynamicPoolResizingService.java): Demonstrates runtime pool resizing with `PoolDataSource.setMinPoolSize` and `PoolDataSource.setMaxPoolSize`.
- [HarvestingService](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/HarvestingService.java): Demonstrates how to mark a borrowed connection as non-harvestable while it is doing work.
- [ConnectionLabelingService](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/java/com/example/ucp/ConnectionLabelingService.java): Demonstrates callback-driven connection labeling for transaction isolation state.
- [application.yaml](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/main/resources/application.yaml): Contains the default configuration and each profile-specific UCP configuration.
- [SpringBootUcpTest](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/spring-boot-ucp/src/test/java/com/example/ucp/SpringBootUcpTest.java): Verifies profile binding and runs smoke queries with Oracle AI Database Free through Testcontainers.

## Prerequisites

- Java 21+
- Maven
- Docker compatible environment for Testcontainers
- Optional local Oracle AI Database Free instance if running the application outside the tests

## Run the Sample

Set connection details with environment variables or use the defaults in `application.yaml`.

```bash
export JDBC_URL=jdbc:oracle:thin:@localhost:1521/freepdb1
export USERNAME=testuser
export PASSWORD=testpwd
```

Run the default profile:

```bash
mvn spring-boot:run
```

Run one profile at a time:

| Profile | Command | Demonstrates |
| --- | --- | --- |
| `sizing` | `mvn spring-boot:run -Dspring-boot.run.profiles=sizing` | `initial-pool-size`, `min-pool-size`, `min-idle`, and `max-pool-size` for dynamic pool sizing. |
| `dynamic-resizing` | `mvn spring-boot:run -Dspring-boot.run.profiles=dynamic-resizing` | Runtime resizing with `PoolDataSource` setters after the UCP data source has been created. |
| `static-sizing` | `mvn spring-boot:run -Dspring-boot.run.profiles=static-sizing` | A narrow min/max range that avoids connection storms and follows Real-World Performance sizing guidance. |
| `timeouts` | `mvn spring-boot:run -Dspring-boot.run.profiles=timeouts` | Wait, idle, validation, reuse, and timeout sweep settings for stale connection control. |
| `abandoned` | `mvn spring-boot:run -Dspring-boot.run.profiles=abandoned` | Abandoned borrowed connection reclamation, time-to-live, and query timeout settings. |
| `harvesting` | `mvn spring-boot:run -Dspring-boot.run.profiles=harvesting` | Harvest trigger and maximum count settings for reclaiming borrowed connections. |
| `statement-cache` | `mvn spring-boot:run -Dspring-boot.run.profiles=statement-cache` | Per-physical-connection SQL statement caching with `max-statements`. |
| `validation` | `mvn spring-boot:run -Dspring-boot.run.profiles=validation` | Borrow-time connection validation with a trusted-idle window to skip validation for recently used connections. |
| `drcp` | `mvn spring-boot:run -Dspring-boot.run.profiles=drcp` | Client-side DRCP settings using a pooled server URL and `oracle.jdbc.DRCPConnectionClass`. |

## Pool Metrics and Diagnostics

At startup, `PoolReporter` logs three records:

- `PoolReport`: pool configuration bound by Spring Boot.
- `PoolMetrics`: live UCP statistics from `PoolDataSource.getStatistics()`.
- `PoolDiagnostics`: JMX, metric interval, registered pool names, and the UCP diagnostic JVM system properties currently in effect.

Spring Boot initializes the UCP data source lazily, so `PoolMetrics.statisticsAvailable` can be `false` at startup before the first connection borrow. After the pool starts, the metrics include active counts and cumulative borrow/return counters, and `PoolDiagnostics.registeredPoolNames` includes the active pool name reported by the UCP manager.

UCP diagnostics are controlled with JVM system properties. For example, this command enables UCP logging, sets the logging level, and keeps tracing enabled with a larger in-memory trace buffer:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Doracle.ucp.diagnostic.enableLogging=true -Doracle.ucp.diagnostic.enableTrace=true -Doracle.ucp.diagnostic.loggingLevel=FINE -Doracle.ucp.diagnostic.bufferSize=2048"
```

You can also configure trace dumps for selected errors:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Doracle.ucp.diagnostic.errorCodesToWatchList=[\"java.sql.SQLException:12514,12521\",\"oracle.ucp.UniversalConnectionPoolException:45054\"]"
```

The diagnostic system properties must be set before UCP initializes the pool, so pass them as JVM arguments rather than Spring application properties.

## Dynamic Pool Resizing

The `dynamic-resizing` profile starts with a small pool:

```yaml
spring:
  datasource:
    oracleucp:
      initial-pool-size: 1
      min-pool-size: 1
      max-pool-size: 2
```

`DynamicPoolResizingService` then unwraps the Spring `DataSource` to a `PoolDataSource`, calls `setMinPoolSize(2)` and `setMaxPoolSize(5)`, borrows five connections to verify the expanded limit, and lowers the live settings with `setMinPoolSize(1)` and `setMaxPoolSize(3)`.

## Connection Validation and Labeling

The `validation` profile enables `validate-connection-on-borrow` and sets `seconds-to-trust-idle-connection`. With the Oracle JDBC driver, UCP can validate borrowed connections using its internal ping, so the sample intentionally does not set `sql-for-validate-connection`.

`ConnectionLabelingService` registers a `ConnectionLabelingCallback`, borrows a connection with a `TRANSACTION_ISOLATION` label, and configures the JDBC connection state before running a query. This shows the application-driven UCP labeling model without requiring RAC, sharding, or external infrastructure.

## DRCP Notes

DRCP is a server-side pool in Oracle AI Database. It is most useful when many middle-tier application instances have connection pools but only a smaller subset of their logical connections are active at the same time. For a single local Spring Boot application, DRCP is mostly educational; UCP alone is usually enough.

To use the `drcp` profile against a local database, a DBA must start the default DRCP pool:

```sql
execute dbms_connection_pool.start_pool();
```

The profile uses the short pooled URL form:

```text
jdbc:oracle:thin:@localhost:1521/freepdb1:POOLED
```

It also sets the DRCP connection class:

```yaml
spring:
  datasource:
    oracleucp:
      connection-properties:
        oracle.jdbc.DRCPConnectionClass: spring-boot-ucp
```

## Run the Tests

From this module:

```bash
mvn test
```

From the repository root:

```bash
mvn -pl spring-boot-ucp test
```

The tests start Oracle AI Database Free with Testcontainers, verify each profile binds to the expected `PoolDataSource` settings, run `select 1 from dual`, verify runtime resizing with `PoolDataSource` setters, and start a conservative DRCP configuration for the DRCP profile.

## References

- [Oracle AI Database JDBC Developer's Guide: Database Resident Connection Pooling](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/database-resident-connection-pooling.html)
- [Oracle AI Database UCP Developer's Guide: Optimizing Universal Connection Pool Behavior](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/optimizing-ucp-behavior.html)
- [Controlling the Pool Size in UCP](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/controlling-pool-size.html)
- [Stale Connections in UCP](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/stale-ucp-connections.html)
- [Validating Connections in UCP](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/validating-ucp-connections.html)
- [Harvesting Connections in UCP](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/harvesting-connections.html)
- [Caching SQL Statements in UCP](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/caching-sql-statements.html)
- [Labeling Connections in UCP](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/labeling-ucp-connections.html)
- [Diagnosing a Connection Pool](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjucp/diagnosing-ucp.html)
