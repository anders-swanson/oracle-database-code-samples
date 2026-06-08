---
name: migrate-kafka-to-oracle/kafka-app-step-1
description: Migration step that swaps Apache Kafka for Oracle AI Database TxEventQ with minimal code changes.
tags:
  - Database
  - Java
  - Kafka
  - TxEventQ
blog_post: "https://andersswanson.dev/2025/05/28/migrate-apache-kafka-applications-to-oracle-database/"
---

# Kafka App Step 1

This module is step 1 of the [Kafka-to-TxEventQ migration sample](../README.md). It replaces the standard Apache Kafka broker with Oracle AI Database Transactional Event Queues while keeping the producer and consumer flow largely unchanged.

Compared with [`../kafka-app/README.md`](../kafka-app/README.md), this step switches to the OKafka client and connects to Oracle AI Database on `localhost:1521/freepdb1`.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker compatible environment
- Oracle AI Database Free running on `localhost:1521`

Start the database:

```bash
docker run --name oracledb -d -p 1521:1521 \
  -e ORACLE_PASSWORD=testpwd \
  gvenzl/oracle-free:23.26.2-slim-faststart
```

Create the application user and required TxEventQ grants:

```bash
sql / as sysdba @../testuser.sql
```

The module expects an `ojdbc.properties` file under `src/main/resources/` with:

```properties
user=testuser
password=testpwd
```

## Run the sample

From this directory:

```bash
mvn clean compile exec:java
```

The application creates `test_topic`, produces ten weather events, and consumes them through Oracle AI Database Transactional Event Queues.

## Next step

Continue with [`../kafka-app-step-2/README.md`](../kafka-app-step-2/README.md) to serialize the weather events as OSON.
