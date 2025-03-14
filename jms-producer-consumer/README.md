# Java Message Service (JMS) Producer Consumer Example

Under construction! Stay tuned.

This example demonstrates how to write a multi-threaded pub/sub application using the JDBC JMS API for [Oracle Database Transactional Event Queues](https://docs.oracle.com/en/database/oracle/oracle-database/23/adque/aq-introduction.html). If you're unfamiliar with Transactional Event Queues, it is a high-throughput, distributed asynchronous messaging system built into Oracle Database. The integration of Transactional Event Queues with Spring JMS provides a simple interface for rapid development of messaging applications.

## Prerequisites

- Java 21+, Maven
- Docker compatible environment

## Run the sample

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers
2. Produce messages to a Transactional Event Queue using JMS
3. Concurrently consume and verify all messages with JMS consumers

You can run the test like so, from the project's root directory:

`mvn test`

You should see output similar to the following:

```
TBD output
```