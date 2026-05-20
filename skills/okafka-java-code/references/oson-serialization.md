# OSON Serialization

Prefer OSON for JSON event payloads. OSON is Oracle's binary JSON format, so it keeps Java domain events aligned with Oracle AI Database JSON storage, SQL/JSON processing, and JSONB binding while still using Kafka-compatible producer and consumer APIs.

Use string payloads only for simple demos, text fixtures, or existing contracts that are explicitly string-based.

## Source Examples

The reference repo has two direct OKafka OSON samples:

- `json/json-event-streaming/src/main/java/com/example/json/stream/Application.java`: creates an OKafka topic, produces POJO events with `OSONSerializer`, consumes `byte[]`, and converts back with `JSONB.fromOSON(...)`.
- `migrate-kafka-to-oracle/kafka-app-step-2/src/main/java/com/example/kafka2/OSONSerializer.java`: small reusable serializer using `JSONB.toOSON(...)`.
- `migrate-kafka-to-oracle/kafka-app-step-2/src/main/java/com/example/kafka2/KafkaApp.java`: migration-style app that swaps JSON event values onto OSON while retaining Kafka client APIs.

For Spring-managed clients, also use:

- `support-ticket-intelligence/src/main/java/com/example/support/messaging/OkafkaConfiguration.java`: injects `OSONKafkaSerializationFactory` and passes serializer/deserializer instances directly into OKafka constructors.

## Maven Dependency

The reference OSON samples use the Oracle JSON Collections starter alongside OKafka. See `dependencies.md` for the OKafka coordinates.

```xml
<dependency>
    <groupId>com.oracle.database.spring</groupId>
    <artifactId>oracle-spring-boot-starter-json-collections</artifactId>
    <version>${oracle.starters.version}</version>
</dependency>
```

If the target is not a Spring Boot application, follow the reference repo's plain-Java samples and exclude `spring-boot-starter` when needed. Preserve existing dependency management if the project already defines the Oracle starter version.

## Plain Java Producer

Use a typed producer with a String key serializer and OSON value serializer:

```java
Properties props = new Properties();
props.putAll(baseOkafkaProperties);
props.put("enable.idempotence", "true");

JSONB jsonb = JSONB.createDefault();
Serializer<String> keySerializer = new StringSerializer();
Serializer<Event> valueSerializer = new OSONSerializer<>(jsonb);

Producer<String, Event> producer =
        new org.oracle.okafka.clients.producer.KafkaProducer<>(props, keySerializer, valueSerializer);
```

Reusable serializer:

```java
final class OSONSerializer<T> implements Serializer<T> {
    private final JSONB jsonb;

    OSONSerializer(JSONB jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public byte[] serialize(String topic, T value) {
        return jsonb.toOSON(value);
    }
}
```

## Plain Java Consumer

Consume values as bytes and convert each payload with `JSONB.fromOSON(...)`:

```java
Properties props = new Properties();
props.putAll(baseOkafkaProperties);
props.put("group.id", "JSON_CONSUMER");
props.put("enable.auto.commit", "false");
props.put("auto.offset.reset", "earliest");
props.put("key.deserializer", StringDeserializer.class.getName());
props.put("value.deserializer", ByteArrayDeserializer.class.getName());

JSONB jsonb = JSONB.createDefault();
Consumer<String, byte[]> consumer = new org.oracle.okafka.clients.consumer.KafkaConsumer<>(props);
consumer.subscribe(List.of(topicName));

ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(3));
for (ConsumerRecord<String, byte[]> record : records) {
    Event event = jsonb.fromOSON(record.value(), Event.class);
    process(event);
}
consumer.commitSync();
```

Handle `IOException` from `fromOSON(...)` as a poison-message or batch failure according to the app's retry policy.

## Spring Producer And Consumer

When Spring provides `OSONKafkaSerializationFactory`, pass actual serializer/deserializer instances to the OKafka constructors instead of putting serializer instances into `Properties`:

```java
@Bean(destroyMethod = "close")
KafkaProducer<String, TicketOpenedEvent> ticketProducer(
        @Qualifier("okafkaProperties") Properties baseProps,
        OSONKafkaSerializationFactory serializationFactory
) {
    Properties props = new Properties();
    props.putAll(baseProps);
    props.put("enable.idempotence", "true");
    props.put("oracle.transactional.producer", "true");
    props.put("key.serializer", StringSerializer.class.getName());
    return new KafkaProducer<>(props, new StringSerializer(), serializationFactory.createSerializer());
}

@Bean(destroyMethod = "")
KafkaConsumer<String, TicketOpenedEvent> ticketConsumer(
        @Qualifier("okafkaProperties") Properties baseProps,
        OSONKafkaSerializationFactory serializationFactory
) {
    Properties props = new Properties();
    props.putAll(baseProps);
    props.put("group.id", "EVENT_CONSUMER");
    props.put("enable.auto.commit", "false");
    props.put("auto.offset.reset", "earliest");
    return new KafkaConsumer<>(
            props,
            new StringDeserializer(),
            serializationFactory.createDeserializer(TicketOpenedEvent.class));
}
```

Do not put an `OSONDeserializer` object into `Properties` as a property value. If using serializer/deserializer instances, use the OKafka constructor overload that accepts those instances.

## Database JSON Writes

When the same flow also writes JSON columns, keep the format consistent:

```java
statement.setObject(1, jsonb.toOSON(payload), OracleTypes.JSON);
```

Use this alongside OKafka OSON payloads when a transactional producer or consumer writes both event data and relational/JSON state through `getDBConnection()`.
