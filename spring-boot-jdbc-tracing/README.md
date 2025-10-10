# Spring Boot Oracle Database JDBC Tracing

This example application demonstrates how to instrument Oracle Database JDBC connections from a Spring Boot app context with OpenTelemetry.

## References

- [Spring Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [OJDBC OpenTelemetry provider](https://github.com/oracle/ojdbc-extensions/tree/main/ojdbc-provider-opentelemetry)

## Prerequisites

- Java 21+, Maven
- Docker compatible environment with docker-compose

## Setup Oracle Database Free and Zipkin with docker-compose

Start the Oracle Database Free and Zipkin containers with docker-compose:

```bash
docker-compose -d
```

When the database starts, the [grant_permissions.sql](./oracle/grant_permissions.sql) is run, creating a test user and a table.

## Run the sample

This command starts the Java application:

```bash
mvn spring-boot:run
```

## Create a trace

POST to the app's REST API to create a trace, starting with a span for the HTTP invocation that drops into the JDBC/database layer:

```bash
curl -X POST http://localhost:8080/flavors \
  -H "Content-Type: application/json" \
  -d '{"flavor": "Mint Chocolate Chip"}'
```

## View traces

1. Navigate to the Zipkin UI, using the container URL `http://localhost:9411/zipkin/`
2. Click "Run Query" to find all traces, or search for a specific trace ID
3. View the trace! You can see the producer scheduling, publishing the event, and consuming the event in a single trace.

## Configure OJDBC Tracing System Properties

##### oracle.jdbc.provider.opentelemetry.enabled

Set this property to `true` to enable the provider. Enabled by default.

##### oracle.jdbc.provider.opentelemetry.sensitive-enabled

Set this property to `true` to export sensitive data, like SQL query text. Disabled by default.
