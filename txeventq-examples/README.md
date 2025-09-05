# Oracle Database Transactional Event Queues Examples

Prerequisites: An Oracle Database instance. The examples are configured to use a local, Oracle Database Free container running on port 1521, but any 23ai instance will work if configured accordingly.

Like all my samples, you can run it on [Oracle Database Free](https://andersswanson.dev/2025/05/22/oracle-database-for-free/)

### [Kafka API](https://github.com/oracle/okafka)

The [okafka.sql](./okafka.sql) script creates a table named `okafka_messages` used to demonstrate transactional messaging capabilities of the OKafka producer.

The [OKafkaProducer](./src/main/java/com/example/txeventq/OKafkaProducer.java) implements a basic Oracle Database Kafka Producer. You can start the producer like so:

```bash
mvn compile exec:java -Pkafkaproducer
```

The [OKafkaConsumer](./src/main/java/com/example/txeventq/OKafkaConsumer.java) implements a basic Oracle Database Kafka Consumer. You can start the consumer like so:

```bash
mvn compile exec:java -Pkafkaconsumer
```

### Spring JMS

The [springjms.sql](./springjms.sql) script contains necessary code to create and start JMS Queue using the `dbms_aq` package.

The [SpringJMSProducer](./src/main/java/com/example/txeventq/SpringJMSProducer.java) implements a basic Spring JMS producer. You can start the producer like so:

```bash
mvn spring-boot:run -Pjmsproducer
```

The [SpringJMSConsumer](./src/main/java/com/example/txeventq/SpringJMSConsumer.java) implements a basic Spring JMS consumer. You can start the consumer like so:

```bash
mvn spring-boot:run -Pjmsconsumer
```

Once both the producer and consumer are started, you can 

### PL/SQL

To use PL/SQL with TxEventQ, see the [PL/SQL](./txeventq.sql) example.

### [Oracle REST Data Services (ORDS)](https://docs.oracle.com/en/database/oracle/oracle-rest-data-services/25.1/orrst/api-oracle-transactional-event-queues.html)

To use ORDS with TxEventQ to produce and consume messages, see the [ORDS](./ords.md) example.
