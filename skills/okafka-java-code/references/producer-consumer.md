# Producer And Consumer

## Payload Choice

Prefer OSON for JSON event payloads. The string examples below are still useful for minimal smoke tests and text-only topics, but new domain events should normally use the OSON patterns in `oson-serialization.md`.

## Producer Properties

```java
Properties producerProps = new Properties();
producerProps.putAll(baseOkafkaProperties);
producerProps.put("enable.idempotence", "true");
producerProps.put("key.serializer", StringSerializer.class.getName());
producerProps.put("value.serializer", StringSerializer.class.getName());

Producer<String, String> producer =
        new org.oracle.okafka.clients.producer.KafkaProducer<>(producerProps);
```

Produce with standard Kafka APIs:

```java
producer.send(new ProducerRecord<>(topicName, key, value));
producer.flush();
```

`close()` flushes pending records, but explicit `flush()` makes sample behavior easier to reason about.

## Consumer Properties

```java
Properties consumerProps = new Properties();
consumerProps.putAll(baseOkafkaProperties);
consumerProps.put("group.id", "MY_CONSUMER_GROUP");
consumerProps.put("enable.auto.commit", "false");
consumerProps.put("auto.offset.reset", "earliest");
consumerProps.put("max.poll.records", "2000");
consumerProps.put("key.deserializer", StringDeserializer.class.getName());
consumerProps.put("value.deserializer", StringDeserializer.class.getName());

Consumer<String, String> consumer =
        new org.oracle.okafka.clients.consumer.KafkaConsumer<>(consumerProps);
```

Process then commit:

```java
consumer.subscribe(List.of(topicName));
while (running) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
    if (records.isEmpty()) {
        continue;
    }
    for (ConsumerRecord<String, String> record : records) {
        process(record);
    }
    consumer.commitSync();
}
```

Do not return from a polling loop before processing and committing the final batch.

## Spring Bean Shape

For Spring applications, prefer raw OKafka clients as beans and inject them into focused producer/consumer services:

```java
@Bean(destroyMethod = "close")
KafkaProducer<String, Event> okafkaProducer(@Qualifier("okafkaProperties") Properties baseProps) {
    Properties props = new Properties();
    props.putAll(baseProps);
    props.put("enable.idempotence", "true");
    props.put("key.serializer", StringSerializer.class.getName());
    return new KafkaProducer<>(props, new StringSerializer(), eventSerializer);
}
```

For consumers owned by a long-running polling thread:

```java
@Bean(destroyMethod = "")
KafkaConsumer<String, Event> okafkaConsumer(@Qualifier("okafkaProperties") Properties baseProps) {
    Properties props = new Properties();
    props.putAll(baseProps);
    props.put("group.id", "EVENT_CONSUMER");
    props.put("enable.auto.commit", "false");
    props.put("auto.offset.reset", "earliest");
    return new KafkaConsumer<>(props, new StringDeserializer(), eventDeserializer);
}
```

Let the polling thread close that consumer in a `finally` block. Stop the thread cooperatively by flipping a running flag; avoid cross-thread `wakeup()` unless the surrounding code is designed and tested for it.
