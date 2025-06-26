# Spring Boot CDC with Transactional Event Queues (TxEventQ) (JMS) Example

This example demonstrates how to implement event-based CDC in Spring Boot  using [Spring JMS](https://spring.io/guides/gs/messaging-jms) and [Oracle Database Transactional Event Queues](https://docs.oracle.com/en/database/oracle/oracle-database/23/adque/aq-introduction.html). In the example app, a service inserts database records, and a Spring JMS consumer reacts to each insert event.

If you're unfamiliar with Transactional Event Queues, it is a high-throughput, distributed asynchronous messaging system built into Oracle Database. The integration of Transactional Event Queues with Spring JMS provides a simple interface for rapid development of messaging applications.

The [Spring Boot Starter for AQ/JMS](https://github.com/oracle/spring-cloud-oracle/tree/main/database/starters/oracle-spring-boot-starter-aqjms) used in the example pulls in all necessary dependencies to use Spring JMS with Oracle Database Transactional Event Queues, requiring minimal configuration.

## Prerequisites

- Java 21+, Maven
- Docker compatible environment

## Run the sample

The sample provides an all-in-one test leveraging Testcontainers and Oracle Database to do the following: 

1. Start and configure a database server using Testcontainers
2. Create a table, queue, and trigger in the database. The trigger will publish ticket-created events using JMS.
3. Insert several tickets to a table an using an autowired TicketService.
4. Consume all ticket-created events with a Spring JMS consumer.

You can run the test like so, from the project's root directory:

`mvn test`

You should see output similar to the following. It may be slightly different due to the asynchronously behavior of the consumer:

```
[MAIN] Starting Spring Boot CDC Example
[MAIN] Created tickets
[MAIN] Waiting for consumer to finish processing messages...
[CONSUMER] Processing ticket: {"id":1,"title":"ticket 1","status":"NEW","created":"2025-06-26T18:42:32.285317"}
[CONSUMER] Processing ticket: {"id":2,"title":"ticket 2","status":"NEW","created":"2025-06-26T18:42:33.282544"}
[CONSUMER] Processing ticket: {"id":3,"title":"ticket 3","status":"NEW","created":"2025-06-26T18:42:33.295571"}
[CONSUMER] Processing ticket: {"id":4,"title":"ticket 4","status":"NEW","created":"2025-06-26T18:42:33.304789"}
[CONSUMER] Processing ticket: {"id":5,"title":"ticket 5","status":"NEW","created":"2025-06-26T18:42:33.312474"}
[CONSUMER] Processing ticket: {"id":6,"title":"ticket 6","status":"NEW","created":"2025-06-26T18:42:33.319317"}
[CONSUMER] Processing ticket: {"id":7,"title":"ticket 7","status":"NEW","created":"2025-06-26T18:42:33.327824"}
[CONSUMER] Processing ticket: {"id":8,"title":"ticket 8","status":"NEW","created":"2025-06-26T18:42:33.337015"}
[CONSUMER] Processing ticket: {"id":9,"title":"ticket 9","status":"NEW","created":"2025-06-26T18:42:33.348141"}
[CONSUMER] Processing ticket: {"id":10,"title":"ticket 10","status":"NEW","created":"2025-06-26T18:42:33.360091"}
[MAIN] Consumer finished.
```