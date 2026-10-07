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
span around the startup produce/consume workload; Spring request observations also create spans.
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

   Compose creates `TESTUSER` with password `Welcome123#` in `FREEPDB1`, then runs the mounted
   [okafka.sql](https://github.com/anders-swanson/oracle-database-code-samples/blob/main/oracle-database-kafka-apis/src/test/resources/okafka.sql)
   to grant TxEventQ privileges during database initialization. The database listens on `localhost:1521`.

2. Run the sample against the Compose database:

```shell
mvn spring-boot:run
```

`MetricsApplication` defaults `okafka.tns-admin` to `src/test/resources`, which contains
`ojdbc.properties` with the Compose user's credentials. The path is relative to the working directory,
so run the application from `oracle-database-kafka-apis/`. For another wallet or credentials directory,
supply `okafka.tns-admin` in application configuration, the `OKAFKA_TNS_ADMIN` environment variable,
or a command-line argument:

```shell
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--okafka.tns-admin=/path/to/wallet"
```

The sample uses local `PLAINTEXT` connectivity. It creates `OKAFKA_METRICS_SAMPLE` if needed, uses
consumer group `METRICS_SAMPLE_GROUP`, and commits records after processing. Run one instance at a time
because instances share the topic and consumer group. These objects remain in Oracle AI Database after
the sample exits.

3. Inspect Actuator in another terminal:

```shell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```

Open [Grafana](http://localhost:3000) and sign in with `admin` / `admin`. In **Explore**, select
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

The default export interval is ten seconds. Counters retain the sample totals; rate metrics can fall
back toward zero after the startup workload finishes. Stop the sample with Ctrl+C; Spring closes the
clients, metric binders, OTLP registry, and OpenTelemetry exporters.

Stop both services with `docker compose down -v`. This removes the sample database's anonymous volume;
the sample does not persist database or LGTM data across this cleanup.

#### Configuration and available metrics

| Property | Default | Purpose |
| --- | --- | --- |
| `okafka.tns-admin` | `src/test/resources` | Wallet and `ojdbc.properties` directory, relative to the working directory |
| `management.otlp.metrics.export.url` | `http://localhost:4318/v1/metrics` | OTLP HTTP endpoint for OKafka and Actuator metrics |
| `management.otlp.metrics.export.step` | `10s` | Metric export interval |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `http://localhost:4318/v1/traces` | OTLP HTTP trace endpoint |
| `management.opentelemetry.logging.export.otlp.endpoint` | `http://localhost:4318/v1/logs` | OTLP HTTP log endpoint |
| `management.tracing.sampling.probability` | `1` | Sample every trace in this local demo |
| `spring.application.name` | `okafka-metrics-sample` | Default service identity; `OTEL_SERVICE_NAME` overrides the telemetry service name |

Set `OTEL_EXPORTER_OTLP_ENDPOINT` to a base URL such as `http://collector:4318` to change all three
signal endpoints. `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`, `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`, and
`OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` override individual endpoints and must include the corresponding
`/v1/...` path.
Spring Boot maps these environment variables directly, including appending signal paths to a base
endpoint with or without a trailing slash. Use an HTTP endpoint on port 4318 for this sample;
Micrometer's OTLP metric registry exports over HTTP. `OTEL_SERVICE_NAME` changes the service identity
for metrics, traces, and logs; an explicit `management.opentelemetry.resource-attributes.service.name`
setting takes precedence. See
[Spring Boot's OpenTelemetry environment variable mapping](https://docs.spring.io/spring-boot/reference/actuator/observability.html#actuator.observability.opentelemetry.environment-variables).
For example, set `--management.otlp.metrics.export.url=http://collector:4318/v1/metrics` for another
Collector. Other Micrometer OTLP exporter settings apply to all metrics. Setting
`management.otlp.metrics.export.enabled=false` disables metric export while keeping Actuator metrics.
Use `management.tracing.export.otlp.enabled=false` or `management.logging.export.otlp.enabled=false`
to disable trace or log export individually.

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

The sample uses OKafka 23.26.1.0 and Kafka clients 3.9.2 with Spring Boot's managed Micrometer versions. A declared metric is not proof that
OKafka updates it: consumer commit-sync timing and producer transaction-init timing have no recording
calls in the inspected client bytecode. Consumer lag is not verified. Cumulative duration metrics do
not provide latency percentiles, and fetch counts do not measure successful application processing.

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
