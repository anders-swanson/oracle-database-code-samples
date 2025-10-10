# Spring Boot Oracle Database JDBC Tracing

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

## View traces

1. Navigate to the Zipkin UI, using the container URL `http://localhost:9411/zipkin/`
2. Click "Run Query" to find all traces, or search for a specific trace ID
3. View the trace! You can see the producer scheduling, publishing the event, and consuming the event in a single trace.

## Configure OJDBC Tracing Properties

##### oracle.jdbc.provider.opentelemetry.enabled

Set this property to `true` to enable the provider. Enabled by default.

##### oracle.jdbc.provider.opentelemetry.sensitive-enabled

Set this property to `true` to export sensitive data. Disabled by default.
