# Kafka App Step 2

This module is step 2 of the [Kafka-to-TxEventQ migration sample](../README.md). It keeps Oracle AI Database Transactional Event Queues as the transport and adds OSON serialization for `WeatherEvent` messages using the Oracle JSON libraries.

Compared with step 1, the producer now writes `WeatherEvent` objects as OSON and the consumer reads raw bytes and deserializes them back to Java objects.

## Prerequisites

- Java 21+
- Maven 3.9+
- Oracle AI Database Free running on `localhost:1521`
- The `testuser` schema and TxEventQ grants created with [`../testuser.sql`](../testuser.sql)

The module expects an `ojdbc.properties` file under `src/main/resources/` with:

```properties
user=testuser
password=testpwd
```

## Run the sample

From this directory:

```bash
mvn clean compile exec:java
```

The output shows the producer serializing `WeatherEvent` records to OSON and the consumer deserializing those records back into Java objects.

## Related docs

- [`../using-oracle-json.md`](../using-oracle-json.md)
- [`../kafka-app-step-3/README.md`](../kafka-app-step-3/README.md)
