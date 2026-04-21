
# Spring Cloud Config Client

This module is the client application from the [Spring Cloud Config sample](../README.md). It loads configuration from the config server and exposes a simple HTTP endpoint that returns the resolved value of `config.key`.

The client uses:

- application name `myapp`
- profile `dev`
- label `latest`
- config server URL `http://localhost:8888`

## Prerequisites

- Java 21+
- Maven 3.9+
- The config server module running on `localhost:8888`
- A matching property stored in the Oracle AI Database-backed `PROPERTIES` table

## Run the client

From this directory:

```bash
mvn clean compile spring-boot:run
```

Once running, request the resolved property:

```bash
curl http://localhost:8080/value
```

## See also

- [`../server/README.md`](../server/README.md)
- [`../README.md`](../README.md)
