# JDBC Event Streaming

Example of streaming events using a plain JDBC connection for Oracle Database Transactional Event Queues (TxEventQ).

## [jdbc.events package](./src/main/java/com/example/jdbc/events)

The [jdbc.events package](./src/main/java/com/example/jdbc/events) package implements the JDBCBatchProducer class to send events, and the JDBCConsumer class to receive. These classes use PL/SQL procedures defined in the [jdbc-events.sql](./src/test/resources/jdbc-events.sql) script, specific the `produce_json_event` and `consume_json_event` procedures, respectively. 

The [JDBCEventStreamingTest](./src/test/java/com/example/jdbc/events/JDBCEventStreamingTest.java) class implements a test scenario using Oracle Database Free to produce and consume events:

1. The JDBCBatchProducer writes a series of records to a queue.
2. Three JDBCConsumer instances run in parallel to consume events.
3. Each consumer inserts events into a database table after receiving them.
4. The test class verifies all events were received and inserted into the database.

## Run the sample

You can run the test with Maven (`mvn test`). Note that you need Java 21+ and a Docker-compatible environment to run the test. After the test is complete, you should see output similar to the following, though the ordering may be different do to parallelization:

```
[PRODUCER] Published all events. Shutting down producer.
[CONSUMER 1] Consumed 30 events. Shutting down consumer.
[CONSUMER 3] Consumed 27 events. Shutting down consumer.
[CONSUMER 2] Consumed 29 events. Shutting down consumer.
```