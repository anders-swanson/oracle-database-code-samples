# Oracle Database Kafka APIs

### Running the Oracle Database Kafka API tests

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