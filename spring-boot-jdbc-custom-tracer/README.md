# Spring Boot Oracle Database JDBC Custom Tracer

This example application demonstrates how to implement a custom tracing implementation for the Oracle JDBC driver, using a Spring Boot app context and OpenTelemetry, as an alternative to the [OJDBC Trace Event Listener](https://github.com/oracle/ojdbc-extensions/blob/main/ojdbc-provider-opentelemetry/src/main/java/oracle/jdbc/provider/opentelemetry/OpenTelemetryTraceEventListener.java)

Why would you want to implement a custom tracer for the Oracle JDBC Driver? To add your custom data to spans!

#### Important Classes
 
- [JDBCTraceEventListener](./src/main/java/com/example/tracing/jdbc/custom/JDBCTraceEventListener.java) implements spans for the Oracle JDBC Driver. Based on the [OpenTelemetryTraceEventListener](https://github.com/oracle/ojdbc-extensions/blob/main/ojdbc-provider-opentelemetry/src/main/java/oracle/jdbc/provider/opentelemetry/OpenTelemetryTraceEventListener.java)
- [JDBCTraceEventListenerProvider](./src/main/java/com/example/tracing/jdbc/custom/JDBCTraceEventListenerProvider.java) provides an instance of the Trace Event Listener to the Oracle JDBC Driver during application startup.
  - [TracingConfigurator](./src/main/java/com/example/tracing/jdbc/custom/TracingConfigurator.java) & [TracingProperties](./src/main/java/com/example/tracing/jdbc/custom/TracingProperties.java) provide Spring Boot configuration for the Trace Event Listener.

## References

- [Spring Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [OJDBC OpenTelemetry provider](https://github.com/oracle/ojdbc-extensions/tree/main/ojdbc-provider-opentelemetry)
- [Spring Boot with OJDBC Tracing](../spring-boot-jdbc-tracing/README.md)

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
