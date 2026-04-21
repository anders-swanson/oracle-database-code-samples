# Kafka App

This module is the starting point for the [Kafka-to-TxEventQ migration sample](../README.md). It uses the standard Apache Kafka Java client to create a topic, produce ten weather events, and consume them back from Apache Kafka.

## Prerequisites

- Java 21+
- Maven 3.9+
- A running Apache Kafka broker on `localhost:9092`

## Run the sample

Start Apache Kafka, for example:

```bash
docker run -p 9092:9092 apache/kafka:4.0.0
```

Then run the application from this directory:

```bash
mvn clean compile exec:java
```

The application creates `test_topic`, starts a consumer, produces ten `WeatherEvent` messages, and then waits for the consumer to read all ten records.

## Next step

Continue with [`../kafka-app-step-1/README.md`](../kafka-app-step-1/README.md) to switch the same application from Apache Kafka to Oracle AI Database Transactional Event Queues using OKafka.
