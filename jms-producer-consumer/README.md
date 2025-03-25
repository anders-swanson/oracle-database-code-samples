# Java Message Service (JMS) Producer / Consumer Example

This example module demonstrates how to write pub/sub applications using the JDBC JMS API for [Oracle Database Transactional Event Queues](https://docs.oracle.com/en/database/oracle/oracle-database/23/adque/aq-introduction.html). If you're unfamiliar with Transactional Event Queues (TxEventQ), it is a high-throughput, distributed, asynchronous messaging system built into Oracle Database. TxEventQ's JMS APIs provide a simple, familiar interface for developing applications with service-to-service messaging capabilities.

## Prerequisites

- Java 21+, Maven
- Docker compatible environment

## JMS Topic Sample

The [com.example.jms.topic](./src/main/java/com/example/jms/topic) package implements a JMSProducer and JMSConsumer for reading and writing data from JMS Topics. Topics 

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers.
   2. You can find the [database initialization script here](./src/test/resources/create-table.sql), which creates a database table for storing JSON events. The [testuser-topic.sql](./src/test/resources/testuser-topic.sql) script assigns appropriate permissions to the `testuser` database user amd creates a JMS topic and subscriber group.
2. Produce messages to a Transactional Event Queue using JMS.
3. Concurrently consume and verify all messages with JMS consumers.


You can run the test like so, from the project's root directory:

```bash
mvn test
```

You should see output similar to the following. Note that the ordering of the consumers may differ due to their parallel nature:

```
[PRODUCER] Sent all JMS messages. Closing producer!
[CONSUMER 1] Received all JMS messages. Closing consumer!
[CONSUMER 3] Received all JMS messages. Closing consumer!
[CONSUMER 2] Received all JMS messages. Closing consumer!
```