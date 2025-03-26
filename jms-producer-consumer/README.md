# Java Message Service (JMS) Producer / Consumer Example

This example module demonstrates how to write pub/sub applications using the JDBC JMS API for [Oracle Database Transactional Event Queues](https://docs.oracle.com/en/database/oracle/oracle-database/23/adque/aq-introduction.html). If you're unfamiliar with Transactional Event Queues (TxEventQ), it is a high-throughput, distributed, asynchronous messaging system built into Oracle Database. TxEventQ's JMS APIs provide a simple, familiar interface for developing applications with service-to-service messaging capabilities.

## Prerequisites

- Java 21+, Maven
- Docker compatible environment

## JMS Topic Sample (Pub/Sub)

The [com.example.jms.topic](./src/main/java/com/example/jms/topic) package implements a JMSProducer and JMSConsumer for reading and writing data from JMS Topics. Topics are useful for high-throughput, multi-consumer pub/sub architectures.

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers. You can find the [database initialization script here](./src/test/resources/create-table.sql), which creates a database table for storing JSON events. The [testuser-topic.sql](./src/test/resources/testuser-topic.sql) script assigns appropriate permissions to the `testuser` database user amd creates a JMS topic and subscriber group.
2. Produce messages to a Transactional Event Queue using JMS.
3. Concurrently start two consumer groups, each with three consumer threads.
4. Verify all messages were sent and inserted in the database.


You can run the test like so, from the project's root directory:

```bash
mvn test
```

You should see output similar to the following. Note that the ordering of the consumers may differ due to their parallel nature:

```
[PRODUCER] Sent all JMS messages. Closing producer!
[CONSUMER 2 (example_subscriber_2)] Received 28 JMS messages. Closing consumer!
[CONSUMER 1 (example_subscriber_2)] Received 36 JMS messages. Closing consumer!
[CONSUMER 3 (example_subscriber_2)] Received 22 JMS messages. Closing consumer!
[CONSUMER 1 (example_subscriber_1)] Received 21 JMS messages. Closing consumer!
[CONSUMER 2 (example_subscriber_1)] Received 36 JMS messages. Closing consumer!
[CONSUMER 3 (example_subscriber_1)] Received 29 JMS messages. Closing consumer!
```

## JMS Queue Sample (Point-to-point)

The [com.example.jms.queue](./src/main/java/com/example/jms/queue) package implements a QueueProducer and QueueConsumer for reading and writing data from JMS Queues. Queues allow for a point-to-point producer/consumer architecture.

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following:

1. Start and configure a database server using Testcontainers. You can find the [database initialization script here](./src/test/resources/create-table.sql), which creates a database table for storing JSON events. The [testuser-queue.sql](./src/test/resources/testuser-queue.sql) script assigns appropriate permissions to the `testuser` database user amd creates a JMS queue.
2. Produce messages to a queue using JMS.
3. Consume and verify all messages with a JMS queue consumer.


You can run the test like so, from the project's root directory:

```bash
mvn test
```

You should see output similar to the following. The ordering and consumer message counts may differ due to parallel message consumption:

```
[PRODUCER] Sent all JMS messages. Closing producer!
[CONSUMER 1] Received 25 messages. Closing consumer!
[CONSUMER 2] Received 24 messages. Closing consumer!
[CONSUMER 3] Received 37 messages. Closing consumer!
```