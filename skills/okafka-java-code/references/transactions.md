# Transactions

Transactions require OKafka concrete classes because `getDBConnection()` is Oracle-specific.

## Transactional Producer

Add the transactional producer flag:

```java
Properties props = new Properties();
props.putAll(baseOkafkaProperties);
props.put("enable.idempotence", "true");
props.put("oracle.transactional.producer", "true");
props.put("key.serializer", StringSerializer.class.getName());
props.put("value.serializer", StringSerializer.class.getName());

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
producer.initTransactions();
```

Use one transaction for Kafka records and database writes:

```java
producer.beginTransaction();
try {
    Connection connection = producer.getDBConnection();
    producer.send(new ProducerRecord<>(topicName, key, value));

    try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
        statement.setString(1, value);
        statement.executeUpdate();
    }

    producer.commitTransaction();
} catch (Exception exception) {
    producer.abortTransaction();
    throw exception;
}
```

Use this when the user needs "publish event and update table" atomicity. Keep SQL work on the connection returned by the OKafka producer; do not mix it with a separate `DataSource` connection inside the same transaction.

## Transactional Consumer

Disable auto commit and commit offsets only after database work succeeds:

```java
Properties props = new Properties();
props.putAll(baseOkafkaProperties);
props.put("group.id", "MY_CONSUMER_GROUP");
props.put("enable.auto.commit", "false");
props.put("auto.offset.reset", "earliest");
props.put("key.deserializer", StringDeserializer.class.getName());
props.put("value.deserializer", StringDeserializer.class.getName());

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
```

Process a batch with the consumer's database connection:

```java
consumer.subscribe(List.of(topicName));
ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
Connection connection = consumer.getDBConnection();
try {
    for (ConsumerRecord<String, String> record : records) {
        persist(connection, record.value());
    }
    consumer.commitSync();
} catch (Exception exception) {
    connection.rollback();
    throw exception;
}
```

Use `commitSync()` for straightforward samples where deterministic completion matters. Use `commitAsync()` only when the application already has callback/error handling for failed commits.

## Testing Transaction Outcomes

Test both paths:

- Abort/failure path: simulate an error before commit and assert the side table has no rows.
- Commit path: process the full input and assert the side table has rows.

The reference sample has separate `TransactionalProduceIT` and `TransactionalConsumeIT` tests with this pattern.
