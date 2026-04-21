---
name: txeventq-multidb-replication
description: Multi-database TxEventQ replication sample using JMS and database links.
tags:
  - Database
  - Java
  - JMS
  - TxEventQ
blog_post: "https://andersswanson.dev/2026/01/14/propagating-cross-database-events-with-oracle-ai-database/"
---

# Transactional Event Queues Multi-Database Replication Example

## Description

This module demonstrates how to propagate messages between Oracle AI Database Transactional Event Queues (TXEventQ) in two separate databases using JMS (Jakarta Messaging) APIs. It showcases queue-to-queue replication over a database link, allowing messages published to a topic in a source database to be automatically propagated to a topic in a destination database. The methods used in this module work with JMS or any kind of TxEventQ payload, including JSON, RAW, and ADT.

The example includes:
- Setup scripts for source and destination databases to create users, queues, and configure propagation.
- Java classes for producing messages (reading from console) to the source topic and consuming messages from the destination topic.

## Prerequisites

- Oracle AI Database 26ai Free (or compatible version) running in Docker containers.
- Maven for building the Java application.
- Java 21 or later.
- Docker and Docker Compose for running the databases.

## Setup

1. **Start the Databases:**

   Use the provided `docker-compose.yml` to start two Oracle AI Database instances:
   ```
   docker-compose up -d
   ```

   This will launch:
   - Source database on port 1522.
   - Destination database on port 1523.

Initialization scripts in the [source_db](./source_db) and [dest_db](./dest_db) are run on the respective databases. These scripts create users, queues, and setup queue-to-queue replication over a database link from the source db to the dest db.

2. **Build the Java Application:**

   ```
   mvn clean compile
   ```

## Run the Example

1. **Run the Consumer (on destination database):**

   ```
   mvn exec:java -Dexec.mainClass="com.example.propagation.JMSConsumer"
   ```

   This subscribes to the `dest` topic and waits for messages.

2. **Run the Producer (on source database) in a separate terminal:**

   ```
   mvn exec:java -Dexec.mainClass="com.example.propagation.JMSProducer"
   ```

   This prompts for console input. Enter messages, and they will be published to the `source` topic. Type 'exit' to stop.

   Messages are propagated to the destination database and printed by the consumer.

Send a few messages with the producer, connected to the source db:

```
Enter message (or 'exit' to quit): m1
Enter message (or 'exit' to quit): m2
```

Then, see the message consumed from the destination db:

```
Subscribed to topic 'destuser.dest', waiting for messages...
Received: m1
Received: m2
```

When you're done, use Ctrl+C to stop each process.

## How It Works

- **Database Setup:** The init scripts create multi-consumer queues (topics) and schedule propagation using `DBMS_AQADM.SCHEDULE_PROPAGATION`.
- **Producer:** Connects to the source database, reads input from the console, creates JMS text messages with a unique correlation ID, publishes them to the topic, and commits.
- **Consumer:** Connects to the destination database, creates a durable subscriber, receives and prints messages, and commits.
- **Propagation:** Messages are replicated from the source topic to the destination topic via the database link.

## Dependencies

- Oracle JDBC and UCP for database connections.
- Oracle AQ Jakarta JMS for messaging.
- Jakarta JMS API.

For more details, refer to the Oracle AI Database documentation on [Transactional Event Queues](https://docs.oracle.com/en/database/oracle/oracle-database/26/adque/aq-jms-operational-interface-basic-operations.html) and [Propagation](https://docs.oracle.com/en/database/oracle/oracle-database/26/adque/propagating-messages.html).
