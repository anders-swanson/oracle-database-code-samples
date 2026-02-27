# JSON Event Streaming with OKafka and OSON

This sample demonstrates how to stream Oracle AI Database serialized JSON data (OSON) using the kafka-clients compatible OKafka client. The application publishes OSON-serialized events into an Oracle AI Database Transactional Event Queue (TxEventQ) topic and consumes them back into typed Java objects, showcasing an end-to-end JSON pub/sub workflow.

## What the module includes

- An `Application` class that creates an OKafka topic, publishes sample JSON payloads serialized with Oracle OSON, and consumes them back via the OKafka consumer API.
- A reusable OSON serializer/deserializer built on Oracle's JSONB binding so you can work with plain Java POJOs while benefiting from Oracle's binary JSON format.
- Integration tests powered by Testcontainers that spin up Oracle AI Database Free, provision the TxEventQ topic, and validate the producer/consumer loop.

## Prerequisites

- Java 21 or newer
- Maven 3.9+
- Docker-compatible environment (Docker Desktop, Colima, etc.) to run Oracle AI Database Free for the Testcontainers integration tests

## Run the tests

From the repository root:

```bash
mvn test
```

The build downloads the OKafka client, launches Oracle AI Database Free in a container, creates the `json_topic` TxEventQ topic, and executes the integration test. Expect producer and consumer logs confirming that OSON payloads were serialized and deserialized successfully.

Test output, showing topic creation, serde, and pub/sub:

```bash
Bootstrap servers: localhost:32877
[ADMIN] Created topic: json_topic
[MAIN] Started consumer
[MAIN] Started producer
[PRODUCER] Serialized: Event{message='event 1'}
[PRODUCER] Serialized: Event{message='event 2'}
[PRODUCER] Serialized: Event{message='event 3'}
[PRODUCER] Serialized: Event{message='event 4'}
[PRODUCER] Serialized: Event{message='event 5'}
[PRODUCER] Produced all messages
[CONSUMER] Deserialized: Event{message='event 1'}
[CONSUMER] Deserialized: Event{message='event 2'}
[CONSUMER] Deserialized: Event{message='event 3'}
[CONSUMER] Deserialized: Event{message='event 4'}
[CONSUMER] Deserialized: Event{message='event 5'}
[CONSUMER] Consumed all messages
[MAIN] Done!
```

## Next steps

- [Use your Oracle AI Database server as a Kafka cluster](https://andersswanson.dev/2025/09/18/pub-sub-in-your-db-oracle-database-txeventq/)
- [Learn how to authenticate with OKafka using plaintext, TLS, and mTLS](https://andersswanson.dev/2025/07/09/authenticate-to-your-oracle-database-like-its-a-kafka-cluster/)
- [Combine transactions with pub/sub for a simple Transactional Outbox implementation](https://andersswanson.dev/2025/09/12/transactional-outbox-simplified-with-oracle-database/)

You can also

- Swap in your own domain POJOs to stream custom JSON documents.
- Point the sample at an external Oracle AI Database Free instance by overriding the Testcontainers connection properties.
- Experiment with transactional OKafka APIs to combine database inserts, update, and more with pub/sub.