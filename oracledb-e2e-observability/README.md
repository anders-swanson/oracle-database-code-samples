---
name: oracledb-e2e-observability
description: Trace a Spring Boot WebMVC request through Oracle JDBC and Oracle AI Database, with UCP metrics, database metrics, logs, Grafana LGTM, and generated TLS certificates.
tags:
  - Observability
  - SpringBoot
  - Java
---

# Observability with Oracle AI Database

This lab connects a small Spring Boot app to Oracle AI Database Free using UCP and the JDBC observability provider. Grafana LGTM receives application/JDBC traces, correlated Logback logs, HTTP/JVM/UCP metrics, and database metrics. Database-native span export is configured through `DBMS_OBSERVABILITY` and an HTTPS proxy (The database spans require HTTPS, hence the proxy for local Grafana LGTM).

| Service | Purpose                                                                                                                                                 |
| --- |---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Oracle AI Database Free | `container-registry.oracle.com/database/free:latest`, seeded application schema, monitoring account, and database observability configuration.          |
| Oracle AI Database Metrics Exporter | Collects database metrics every 15 seconds and pushes them to LGTM over OTLP/gRPC.                                                                      |
| Grafana LGTM | Grafana, Loki, Tempo, Prometheus, and an OpenTelemetry Collector with disposable container storage and a provisioned lab dashboard.                               |
| NGINX | HTTPS entry point for Grafana, exporter metrics, and database OTLP/HTTP; dynamically generates certificates signed by a container-local CA on startup. |
| Spring Boot application (host Maven process) | `GET /api/readings`, a real query through UCP, session identity, and managed Micrometer/OpenTelemetry export.                                           |

**ARM limitation:** Server-side tracing is configured, but database server spans are not yet working on ARM architectures. Application and JDBC telemetry work on ARM.

## Start the lab

Install JDK 21+, Maven, and Docker with Compose v2. Allocate at least 8 GB of memory to Docker for this stack. Compose supplies all required defaults, including ports and lab credentials; no environment file is needed. The official full image includes the `orapki` wallet tool. Compose sets `ORACLE_PWD` and mounts the bootstrap scripts under `/opt/oracle/scripts/startup`.

```bash
cd oracledb-e2e-observability
docker compose up -d --wait --wait-timeout 900
```

Compose runs the four infrastructure services. The first database start initializes the schemas and restarts the instance once to activate `WALLET_ROOT`. Startup scripts install the CA trust wallets, network ACLs, and OTLP endpoint.

Run the app in a separate terminal from this module directory:

```bash
mvn spring-boot:run
```

The Maven plugin enables the Oracle JDBC OTEL tracer and stable database semantic conventions. Defaults connect to `localhost:1521/FREEPDB1`, export telemetry to `http://localhost:4318`, and serve the app on port 8081. Stop the app with Ctrl+C.

Open [Grafana](http://localhost:3000), then open **Dashboards → Oracle AI Database → Oracle AI Database end-to-end observability**.

| Endpoint | Address |
| --- | --- |
| Grafana | `http://localhost:3000/` |
| Grafana through HTTPS proxy | `https://localhost:8443/` |
| Direct app / Actuator | `http://localhost:8081/api/readings` / `http://localhost:8081/actuator/health` |
| Exporter metrics | `https://localhost:8443/metrics` |
| Host OTLP/HTTP base URL | `http://localhost:4318` |
| Host OTLP/gRPC endpoint | `localhost:4317` |
| Database HTTPS OTLP base URL | `https://nginx:443` (inside Compose) |
| HTTP redirect | `http://localhost:8080/` |
| Oracle AI Database Free | `localhost:1521/FREEPDB1` |

## Generate traffic and explore telemetry

```bash
./workload.sh
```

Stop the continuous workload with Ctrl+C. The response contains two readings, the current trace ID, and the database-observed module/action/client ID and session ID. `X-Client-Id` accepts 1–64 ASCII letters, digits, dots, underscores, or hyphens. Each decorated connection borrow resets session identity before applying the request's client ID.

Application logs remain available as JSON in the Maven terminal, including trace and span IDs for logs written inside a span. The same logs are exported through the OTLP Logback appender to Loki.

In **Explore**, select **Tempo**, paste the returned trace ID, and inspect the HTTP, `lab.readings`, and JDBC `Execute query` spans. Use the Tempo-to-Loki navigation to find the request log with the matching trace ID; Loki includes `trace_id` and `span_id` metadata. A complete database-exporting environment should additionally show an `oracle-db` server span parented by the JDBC span.

The dashboard opens with database health, request rate, latency, and error percentage. HTTP panels compare mean and p95 latency. UCP panels show capacity, utilization, pending requests, mean/peak acquisition time, borrow/return rates, and failed acquisitions. Database panels show sessions, PDB tablespace utilization, exporter query errors, and collection duration. Use **Request traces** and **Application logs** at the top to open the corresponding Explore view.

Rate panels need ongoing traffic and about one minute of samples after startup. UCP statistics become available after the first database request. UCP meters are also available under `http://localhost:8081/actuator/metrics`.

## Database server spans and TLS

The JDBC provider uses Boot's managed SDK and propagates trace context. UCP connection properties select `observability-trace-event-listener-provider` and enable server traces; the connection decorator forces the documented telemetry state transition and sets the trace-level context used in Oracle's example. The controller brackets SQL execution with JDBC `beginRequest()` / `endRequest()` before returning the session to UCP.

Database startup enables the service in the CDB and PDB, registers and enables `https://nginx:443/v1/traces`, enables capture and extra metadata, and grants the application user network access to `nginx:443`. Auto-login trust wallets live at `WALLET_ROOT/<PDB_GUID>/disttrc` and `tls`. The instance's wallet root is stored inside the database container.

NGINX generates a fresh 30-day leaf certificate on every start. The local CA lives inside the NGINX container. Database startup downloads its public certificate from `http://nginx/lab-ca.crt` and imports it into the telemetry wallets.

Application OTLP goes from the host to LGTM through published port 4318. Database-native traces use the HTTPS proxy. JDBC connections use the ordinary Oracle listener. Database alert-log forwarding remains disabled; the required logs here are application logs.

Oracle's [JDBC API documentation](https://docs.oracle.com/en/database/oracle/oracle-database/26/jajdb/oracle/jdbc/OracleConnection.html), [observability administration guide](https://docs.oracle.com/en/database/oracle/oracle-database/26/admin/monitoring-the-database.html), and [server-export example](https://blogs.oracle.com/developers/end-to-end-agentic-ai-observability-tracing-from-agents-into-the-oracle-ai-database-using-the-java-observability-provider-and-server-side-otel-exporter) explain the client and database setup.

## [Optional] Run the Java tests

Run tests from the repository root with JDK 21+ and Maven:

```bash
mvn -pl oracledb-e2e-observability test
```

## Troubleshooting and cleanup

Stop the Maven application with Ctrl+C, and then stop the containers with `docker compose down`.

If server spans are missing, check the database endpoint configuration, wallet trust, proxy access logs, and database trace files. In Tempo, inspect the request trace for an `oracle-db` span parented by its JDBC span. Server spans are not yet working on the ARM database free image.

Compose uses `container-registry.oracle.com/database/free:latest` with container-local data storage. The container tags can change upstream; pin digests for a repeatable workshop.

Compose publishes Grafana (3000), OTLP/gRPC (4317), and OTLP/HTTP (4318) on loopback.
