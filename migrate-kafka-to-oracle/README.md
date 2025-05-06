# Migrate Apache Kafka to Oracle Database Transactional Event Queues (TxEventQ)

![Migrate to TxEventQ](migrate-kafka-to-oracle-txeventq.png)

This module demonstrates migrating application code from Apache Kafka to [TxEventQ](https://oracle.github.io/microservices-datadriven/transactional-event-queues/getting-started/index.html), using the Kafka Java Client for Oracle Database Transactional Event Queues.

This module includes two sub-modules, [kafka-app](./kafka-app), which contains a simple Apache Kafka application, and [txeventq-app](./txeventq-app), which contains an updated version of the application that is modified to use Oracle Database instead of Apache Kafka.

## The Kafka App

The [kafka-app](./kafka-app) module contains a simple Apache Kafka application that creates a topic, produces 10 records, and consumes 10 records from that topic.

### Running the Kafka App

To run the kafka-app, start an Apache Kafka container like so:

```bash
docker run -p 9092:9092 apache/kafka:4.0.0
```

Then, from the `kafka-app` directory, start the app using Maven: 

```bash
mvn clean compile exec:java
```

You should see output similar to the following, indicating the producer and consumer processed all expected records:

```
[ADMIN] Topic already exists
[MAIN] Started consumer
[MAIN] Started producer
[PRODUCER] Sent: Message #1
[PRODUCER] Sent: Message #2
[PRODUCER] Sent: Message #3
[PRODUCER] Sent: Message #4
[PRODUCER] Sent: Message #5
[PRODUCER] Sent: Message #6
[PRODUCER] Sent: Message #7
[PRODUCER] Sent: Message #8
[PRODUCER] Sent: Message #9
[PRODUCER] Sent: Message #10
[PRODUCER] Produced all messages
[CONSUMER] Received: Message #1
[CONSUMER] Received: Message #2
[CONSUMER] Received: Message #3
[CONSUMER] Received: Message #4
[CONSUMER] Received: Message #5
[CONSUMER] Received: Message #6
[CONSUMER] Received: Message #7
[CONSUMER] Received: Message #8
[CONSUMER] Received: Message #9
[CONSUMER] Received: Message #10
[CONSUMER] Consumed all messages
[MAIN] Done!
```

## Migrating the Kafka App to TxEventQ

The [txeventq-app](./txeventq-app) contains a migrated version of the kafka-app, with three key differences:

1. The `kafka-clients` dependency in the [pom.xml](./txeventq-app/pom.xml) has been replaced with the okafka dependency:

```xml
<!-- OKafka All-in-one -->
<dependency>
    <groupId>com.oracle.database.messaging</groupId>
    <artifactId>okafka</artifactId>
    <version>23.6.0.0</version>
</dependency>
```

2. The AdminClient, KafkaProducer, and KafkaConsumer constructors have been replaced with okafka implementations:

```java
// Use org.oracle.okafka.clients implementations for kafka-clients
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
```

3. Finally, the connection information for the Apache Kafka cluster is updated to use Oracle Database. Note that the `oracle.net.tns_admin` property should point to the directory containing the Oracle Database wallet. If you're not using a wallet, this directory should contain an [ojdbc.properties](./txeventq-app/src/main/resources/ojdbc.properties) file with the `user` and `password` to connect to the database.

```java
private static Properties connectionProperties() {
    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:1521");
    props.setProperty("security.protocol", "PLAINTEXT");
    // Database service name / TNS Alias
    props.put("oracle.service.name", "freepdb1");
    // If using Oracle Database wallet, pass wallet directory
    String resourcesDir = new File(TxEventQApp.class.getClassLoader().getResource("").getFile())
            .getAbsolutePath();
    props.put("oracle.net.tns_admin", resourcesDir);
    return props;
}
```

If you're using SSL instead of PLAINTEXT, use the following connection code for Oracle Database:

```java
private static Properties connectionProperties() {
    Properties props = new Properties();
    props.setProperty("security.protocol", "SSL");
    // TNS Alias
    props.put("oracle.service.name", "mydb_tp");
    props.put("tns.alias", "mydb_tp");
    // If using Oracle Database wallet, pass wallet directory
    String resourcesDir = new File(KafkaApp.class.getClassLoader().getResource("").getFile())
            .getAbsolutePath();
    props.put("oracle.net.tns_admin", resourcesDir);
    return props;
}
```

### Running the TxEventQ App

To run the txeventq-app, start an Oracle Database Free container:

```bash
docker run --name oracledb -d -p 1521:1521 -e ORACLE_PASSWORD=testpwd gvenzl/oracle-free:23.7-slim-faststart
```

Next, run the [testuser.sql](./testuser.sql) script against the database as sysdba. This script creates a database user for the txeventq-app with all necessary grants to create topics and produce/consume messages.

Then, from the `txeventq-app` directory, start the app using Maven:

```bash
mvn clean compile exec:java
```

You should see output similar to the following when you run the txeventq-app:

```
**[ADMIN] Topic already exists
[MAIN] Started consumer
[MAIN] Started producer
[PRODUCER] Sent: Message #1
[PRODUCER] Sent: Message #2
[PRODUCER] Sent: Message #3
[PRODUCER] Sent: Message #4
[PRODUCER] Sent: Message #5
[PRODUCER] Sent: Message #6
[PRODUCER] Sent: Message #7
[PRODUCER] Sent: Message #8
[PRODUCER] Sent: Message #9
[PRODUCER] Sent: Message #10
[PRODUCER] Produced all messages
[CONSUMER] Received: Message #1
[CONSUMER] Received: Message #2
[CONSUMER] Received: Message #3
[CONSUMER] Received: Message #4
[CONSUMER] Received: Message #5
[CONSUMER] Received: Message #6
[CONSUMER] Received: Message #7
[CONSUMER] Received: Message #8
[CONSUMER] Received: Message #9
[CONSUMER] Received: Message #10
[CONSUMER] Consumed all messages
[MAIN] Done!
```
