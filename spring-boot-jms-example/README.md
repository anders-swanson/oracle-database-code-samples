# Spring Java Message Service (JMS) Example

This example demonstrates how to write a pub/sub application using [Spring JMS](https://spring.io/guides/gs/messaging-jms) and [Oracle Database Transactional Event Queues](https://docs.oracle.com/en/database/oracle/oracle-database/23/adque/aq-introduction.html). If you're unfamiliar with Transactional Event Queues, it is a high-throughput, distributed asynchronous messaging system built into Oracle Database. The integration of Transactional Event Queues with Spring JMS provides a simple interface for rapid development of messaging applications.

The [Spring Boot Starter for AQ/JMS](https://github.com/oracle/spring-cloud-oracle/tree/main/database/starters/oracle-spring-boot-starter-aqjms) used in the example pulls in all necessary dependencies to use Spring JMS with Oracle Database Transactional Event Queues, requiring minimal configuration.

## Prerequisites

- Java 21+, Maven
- Docker compatible environment

## Run the sample

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers
2. Produce several messages to a Transactional Event Queue using an autowired JMSTemplate.
3. Verify all messages are consumed by the JMSListener.

You can run the test like so, from the project's root directory:

`mvn test`

You should see output similar to the following:

```
Starting Spring Boot JMS Example
Produced 5 messages to the queue via JMS.
Waiting for consumer to finish processing messages...
Received message: test message 1
Received message: test message 2
Received message: test message 3
Received message: test message 4
Received message: test message 5
Consumer finished.
```