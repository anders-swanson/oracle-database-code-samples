---
name: oracle-database-kafka-apis
description: Java samples and integration tests for Oracle AI Database Transactional Event Queues, including OpenTelemetry client metrics.
tags:
  - Database
  - Java
  - Kafka
  - Testcontainers
  - TxEventQ
blog_post: "https://andersswanson.dev/2025/07/09/authenticate-to-your-oracle-database-like-its-a-kafka-cluster/"
---

# Oracle AI Database Kafka APIs

The following articles describe using Kafka Java APIs with Oracle AI Database Transactional Event Queues:

- [Transactional Messaging with the Kafka Java Client for Oracle AI Database Transactional Event Queues](https://medium.com/@anders.swanson.93/using-transactional-kafka-apis-with-oracle-database-70f58598a176)
- [Produce and consume messages with the Kafka Java Client for Oracle AI Database Transactional Event Queues](https://medium.com/@anders.swanson.93/seamlessly-stream-data-with-kafka-apis-for-oracle-79db9ce02dc0)

### Running the Oracle AI Database Kafka API tests

The tests in this package demonstrate using the Kafka Java Client for Oracle AI Database Transactional Event Queues to produce and consume messages. The tests use a containerized Oracle AI Database instance with Testcontainers to run locally on a Docker-compatible environment with Java 21.

Prerequisites:
- Java 21
- Maven
- Docker

Once your docker environment is configured, you can run the integration tests with maven:


1. To demonstrate producing and consuming messages from a Transactional Event Queue topic using Kafka APIs, run the OKafkaExampleIT test.
```shell
mvn integration-test -Dit.test=OKafkaExampleIT
```

2. To demonstrate a transactional producer, run the TransactionalProduceIT test. With a transactional producer, messages are only produced if the producer successfully commits the transaction.
```shell
mvn integration-test -Dit.test=TransactionalProduceIT
```

3. To demonstrate a transactional consumer, run the TransactionalConsumeIT test.
```shell
mvn integration-test -Dit.test=TransactionalConsumeIT
```

To run all the Transactional Event Queue Kafka API tests and check their results, run `mvn verify`.

### OKafka metrics, tracing, and logs with Spring Boot Actuator and OpenTelemetry

The `com.example.metrics` package contains a Spring Boot sample for `kafka-clients` metrics, tracing, and logs with OKafka. The sample exporter OpenTelemetry observability data on OKafka producers and consumers, so you can see how these components are functioning in real time.

After verifying ten records at startup, the sample schedules a sensor reading every 500 ms and keeps
polling and committing consumed records. Stop the application with Ctrl+C to end the workload.

The [MetricsApplication](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/src/main/java/com/example/metrics/MetricsApplication.java)
registers Micrometer `KafkaClientMetrics` binders for OKafka's standard `metrics()` API. Spring Boot
binds these to the same `MeterRegistry` as Actuator's JVM and application metrics and exports them
through its auto-configured OTLP registry. Metrics also appear at `/actuator/metrics`.
Spring closes the binders and their refresh schedulers during application shutdown.

The sample uses `spring-boot-starter-opentelemetry` for telemetry.
It includes Micrometer's OTLP registry and `micrometer-tracing-bridge-otel`; the latter bridges tracing,
while metrics use Micrometer's registry directly. No custom OpenTelemetry SDK metric provider,
exporter, or Kafka instrumentation dependency is needed. See
[Spring Boot's OpenTelemetry documentation](https://docs.spring.io/spring-boot/reference/actuator/observability.html#actuator.observability.opentelemetry.support).
The Kafka binders instrument client metrics. A Micrometer observation creates an `okafka.sample`
span around the startup workload and an `okafka.reading` span around each scheduled publish/consume
cycle; Spring request observations also create spans.
The local sample samples every trace (`management.tracing.sampling.probability=1`). It does not
propagate trace context through individual Kafka messages.

For logs, `opentelemetry-logback-appender-1.0` and
[logback-spring.xml](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/src/main/resources/logback-spring.xml)
forward Logback records to Spring Boot's OpenTelemetry instance while retaining console output.
[MetricsApplication](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/src/main/java/com/example/metrics/MetricsApplication.java)
connects the appender during startup. Logs within the sample span carry its trace and span IDs.
Spring Boot manages the trace and log exporters; no custom SDK provider or exporter is created.
See [Spring Boot's logging documentation](https://docs.spring.io/spring-boot/reference/actuator/loggers.html#actuator.loggers.opentelemetry).

#### Run the sample

Run these commands from `oracle-database-kafka-apis/`.

1. Start Oracle AI Database Free and Grafana LGTM with Docker Compose. LGTM includes an
   OpenTelemetry Collector, Prometheus for metrics, and Grafana with preconfigured data sources:

```shell
docker compose up -d
```

   Compose uses `container-registry.oracle.com/database/free:latest`. Its
   [startup script](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/docker/init.sql)
   creates the `USERS` tablespace and `TESTUSER` with password `Welcome123#` in `FREEPDB1`, then runs the mounted
   [okafka.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/src/test/resources/okafka.sql)
   to grant TxEventQ privileges. `ORACLE_PWD` sets the administrator password. The startup script
   also runs on subsequent starts and preserves an existing user. The health check verifies that
   `TESTUSER` can connect and query a required OKafka view. The database listens on `localhost:1521`.

2. Run the sample against the Compose database:

```shell
mvn spring-boot:run
```

`MetricsApplication` defaults `okafka.tns-admin` to `src/test/resources`, which contains
`ojdbc.properties` with the Compose user's credentials. The path is relative to the working directory,
so run the application from `oracle-database-kafka-apis/`.

3. Inspect Actuator in another terminal:

```shell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```

Open [Grafana](http://localhost:3000) and sign in with `admin` / `admin`. Compose provisions the
[OKafka Metrics Sample dashboard](http://localhost:3000/d/okafka-metrics-sample) and sets it as the
home dashboard. It shows produced/consumed totals, throughput, producer errors, time since the last
consumer poll, JVM memory, and CPU usage. The **Service name** field defaults to `okafka-metrics-sample`;
change it if you set `OTEL_SERVICE_NAME`. Rates need multiple exports, so allow about a minute for
the throughput panels to populate. The dashboard refreshes every ten seconds.

The dashboard's
[JSON source](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/docker/grafana/dashboards/okafka-metrics.json)
can also be imported into another Grafana instance using a Prometheus data source with UID `prometheus`.

In **Explore**, select
the **Prometheus** data source and query `kafka_consumer_fetch_manager_records_consumed_total` or
`kafka_producer_record_send_total`. Prometheus converts the OpenTelemetry metric names from dots to
underscores. Actuator JVM metrics are available in the same data source.

For traces, select **Tempo** and use TraceQL `{resource.service.name = "okafka-metrics-sample"}`
to find the `okafka.sample` span. For logs, select **Loki** and query
`{service_name="okafka-metrics-sample"}`. The startup producer/consumer logs share the sample span's
trace ID.

Grafana uses port 3000; OTLP gRPC and HTTP use ports 4317 and 4318. All three are bound to localhost.
Metrics, traces, and logs use OTLP HTTP on `http://localhost:4318` with the respective
`/v1/metrics`, `/v1/traces`, and `/v1/logs` paths. See [Grafana's Docker LGTM documentation](https://grafana.com/docs/opentelemetry/docker-lgtm/).

When you're done, remove both container services with `docker compose down`.

#### Configuration and available metrics

Configuration used:

| Property | Default | Purpose |
| --- | --- | --- |
| `okafka.tns-admin` | `src/test/resources` | Wallet and `ojdbc.properties` directory, relative to the working directory |
| `management.otlp.metrics.export.url` | `http://localhost:4318/v1/metrics` | OTLP HTTP endpoint for OKafka and Actuator metrics |
| `management.otlp.metrics.export.step` | `10s` | Metric export interval |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `http://localhost:4318/v1/traces` | OTLP HTTP trace endpoint |
| `management.opentelemetry.logging.export.otlp.endpoint` | `http://localhost:4318/v1/logs` | OTLP HTTP log endpoint |
| `management.tracing.sampling.probability` | `1` | Sample every trace in this local demo |
| `spring.application.name` | `okafka-metrics-sample` | Default service identity; `OTEL_SERVICE_NAME` overrides the telemetry service name |

Metrics:

| Example metric | Meaning |
| --- | --- |
| `kafka.producer.record.send.total` | Records sent by the producer |
| `kafka.producer.record.error.total` | Producer record errors |
| `kafka.producer.buffer.available.bytes` | Available producer buffer memory |
| `kafka.producer.flush.time.ns.total` | Cumulative time spent flushing, in nanoseconds |
| `kafka.consumer.fetch.manager.records.consumed.total` | Records fetched by the consumer |
| `kafka.consumer.fetch.manager.bytes.consumed.total` | Bytes fetched by the consumer |
| `kafka.consumer.fetch.manager.fetch.latency.avg` | Average fetch latency |
| `kafka.consumer.last.poll.seconds.ago` | Seconds since the last poll |

Client IDs (`metrics-producer` and `metrics-consumer`) identify the clients; topic tags are included
where the client exposes them. The Micrometer binder selects the most detailed available dimensions to
avoid counting both client totals and per-topic totals for the same records.

#### Verify metric export

```shell
mvn test -Dtest=MetricsInstrumentationTest
```

This test provisions Oracle AI Database Free, runs the Spring Boot sample, decodes actual OTLP HTTP
protobuf requests, and verifies producer and consumer counts, flush timing, client tags, and the
service resource. It also verifies Actuator JVM metrics over OTLP and checks the Actuator metrics
endpoint. It verifies the sample span and an exported log record with matching trace and span IDs.
The test supplies its own local OTLP receiver, so it does not require a running Collector. Run `mvn verify` to include the existing
transactional messaging tests.
