# Oracle Database Kafka APIs

The following articles describe using Kafka Java APIs with Oracle Database Transactional Event Queues:

- [Transactional Messaging with the Kafka Java Client for Oracle Database Transactional Event Queues](https://medium.com/@anders.swanson.93/using-transactional-kafka-apis-with-oracle-database-70f58598a176)
- [Produce and consume messages with the Kafka Java Client for Oracle Database Transactional Event Queues](https://medium.com/@anders.swanson.93/seamlessly-stream-data-with-kafka-apis-for-oracle-79db9ce02dc0)

### Running the Oracle Database Kafka API tests

The tests in this package demonstrate using the Kafka Java Client for Oracle Database Transactional Event Queues to produce and consume messages. The tests use a containerized Oracle Database instance with Testcontainers to run locally on a Docker-compatible environment with Java 21.

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

To run all the Transactional Event Queue Kafka API tests, run `mvn integration-test`.