---
name: migrate-kafka-to-oracle/kafka-app-step-3
description: Migration step that adds transactional database writes to the Kafka-to-TxEventQ consumer.
tags:
  - Database
  - Duality Views
  - Java
  - JSON
  - Kafka
  - TxEventQ
blog_post: "https://andersswanson.dev/2025/06/09/migrate-apache-kafka-applications-to-oracle-database-part-iii/"
---

# Kafka App Step 3

This module is step 3 of the [Kafka-to-TxEventQ migration sample](../README.md). It builds on the OSON-based messaging flow from step 2 and adds transactional database writes on the consumer side.

For each consumed message, the sample reuses the consumer's Oracle AI Database connection and inserts the OSON payload into `WEATHER_EVENT_DV`, a JSON Relational Duality View. That demonstrates consuming messages and applying database changes in the same transaction boundary.

## Prerequisites

- Java 21+
- Maven 3.9+
- Oracle AI Database Free running on `localhost:1521`
- The `testuser` schema and TxEventQ grants created with [`../testuser.sql`](../testuser.sql)
- The weather tables and `WEATHER_EVENT_DV` duality view created as described in [`../transactional-messaging.md`](../transactional-messaging.md)

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

The producer serializes ten weather events to OSON. The consumer deserializes each message, inserts it into `WEATHER_EVENT_DV`, and commits after the poll batch is processed.

## Related docs

- [`../using-oracle-json.md`](../using-oracle-json.md)
- [`../transactional-messaging.md`](../transactional-messaging.md)
