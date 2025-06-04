# Using Oracle JSON (OSON)

In this section, we'll add support for OSON, Oracle's optimized binary JSON format.

To help us implement OSON support, we'll add the Oracle JSON collections dependency to our project. This package includes several classes for effectively leveraging OSON in Java apps, including binary serialization utilities. 

Note that we exclude the spring boot starter dependency - we aren't using Spring in this example, and are only using the Oracle JSON bits.

```xml
<dependency>
    <groupId>com.oracle.database.spring</groupId>
    <artifactId>oracle-spring-boot-starter-json-collections</artifactId>
    <version>25.2.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Updating the Producer to use OSON

Next, we'll implement a Kafka OSON serializer, so our Kafka producer can write Java objects as OSON when sending messages to a topic. Our OSON serializer will use the `JSONB` utility class, which includes methods to convert Java objects both to and from OSON:

```java
import com.oracle.spring.json.jsonb.JSONB;
import org.apache.kafka.common.serialization.Serializer;

public class OSONSerializer<T> implements Serializer<T> {
    private final JSONB jsonb;

    public OSONSerializer(JSONB jsonb) {
        this.jsonb = jsonb;
    }

    @Override
    public byte[] serialize(String s, T obj) {
        return jsonb.toOSON(obj);
    }
}

```

Let's update our KafkaProducer to use the new OSONSerializer, modifying our producer to write WeatherEvent objects as OSON.

```java
private static Producer<String, WeatherEvent> createProducer(Properties props) {
    props.put("enable.idempotence", "true");
    JSONB jsonb = JSONB.createDefault();
    Serializer<String> keySerializer = new StringSerializer();
    Serializer<WeatherEvent> valueSerializer = new OSONSerializer<>(jsonb);

    return new KafkaProducer<>(props, keySerializer, valueSerializer);
}
```

The `startProducer` method must also be updated to set WeatherEvent as the ProducerRecord value:

```java
private static Future<?> startProducer() {
    return EXECUTOR.submit(() -> {
        try (Producer<String, WeatherEvent> producer = createProducer(connectionProperties())) {
            for (WeatherEvent event : WeatherEvent.getSampleEvents()) {
                ProducerRecord<String, WeatherEvent> record = new ProducerRecord<>(TOPIC_NAME, event);
                producer.send(record);
                System.out.println("[PRODUCER] Serializing: " + event.toString());
            }
        }
        System.out.println("[PRODUCER] Produced all messages");
    });
}
```

### Updating the consumer to use OSON

Next, we update our consumer to receive a byte array, the OSON representation of the produced WeatherEvent objects. We intentionally keep the event in binary format, as we'll later add capabilities leveraging the binary data to insert the record into the database in the next section ([Transactional Messaging](./transactional-messaging.md)].

```java
private static Consumer<String, byte[]> createConsumer(Properties props) {
    props.put("group.id" , "MY_CONSUMER_GROUP");
    props.put("enable.auto.commit", "false");
    props.put("auto.offset.reset", "earliest");
    props.put("max.poll.records", 50);
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    return new KafkaConsumer<>(props);
}
```

The start consumer method is also updated to handle binary OSON data. For example purposes, we still print weather event's string representation to the console.

```java
private static Future<?> startConsumer() {
    JSONB jsonb = JSONB.createDefault();
    return EXECUTOR.submit(() -> {
        int consumedMessages = 0;
        try (Consumer<String, byte[]> consumer = createConsumer(connectionProperties())) {
            consumer.subscribe(List.of(TOPIC_NAME));
            while (consumedMessages < TOTAL_RECORDS) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(3));
                for (ConsumerRecord<String, byte[]> record : records) {
                    String val = jsonb.fromOSON(record.value(), WeatherEvent.class)
                            .toString();
                    System.out.println("[CONSUMER] Deserialized: " + val);
                }
                consumer.commitSync();
                consumedMessages += records.count();
            }
        } catch (IOException e) {
            System.out.println("[CONSUMER] Deserialization error: " + e.getMessage());
        }
        System.out.println("[CONSUMER] Consumed all messages");
    });
}

```

Running the app, we should see the following output, demonstrating that our serialization implementation was successful:

```bash
[ADMIN] Topic already exists
[MAIN] Started consumer
[MAIN] Started producer
[PRODUCER] Serialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-03T06:00, temperature=18.2, humidity_percent=81.0, uv_index=0.0}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-03T08:00, temperature=21.7, humidity_percent=70.3, uv_index=2.5}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=2}, timestamp=2025-06-03T10:00, temperature=25.4, humidity_percent=62.8, uv_index=4.9}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=2}, timestamp=2025-06-03T12:00, temperature=28.6, humidity_percent=51.2, uv_index=7.3}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=3}, timestamp=2025-06-03T14:00, temperature=31.6, humidity_percent=40.5, uv_index=10.5}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=3}, timestamp=2025-06-03T16:00, temperature=30.1, humidity_percent=45.1, uv_index=9.0}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-03T18:00, temperature=27.0, humidity_percent=57.0, uv_index=3.8}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=2}, timestamp=2025-06-03T20:00, temperature=23.5, humidity_percent=63.3, uv_index=0.9}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=3}, timestamp=2025-06-03T22:00, temperature=20.8, humidity_percent=69.1, uv_index=0.0}
[PRODUCER] Serialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-04T00:00, temperature=18.6, humidity_percent=74.8, uv_index=0.0}
[PRODUCER] Produced all messages
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-03T06:00, temperature=18.2, humidity_percent=81.0, uv_index=0.0}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-03T08:00, temperature=21.7, humidity_percent=70.3, uv_index=2.5}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=2}, timestamp=2025-06-03T10:00, temperature=25.4, humidity_percent=62.8, uv_index=4.9}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=2}, timestamp=2025-06-03T12:00, temperature=28.6, humidity_percent=51.2, uv_index=7.3}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=3}, timestamp=2025-06-03T14:00, temperature=31.6, humidity_percent=40.5, uv_index=10.5}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=3}, timestamp=2025-06-03T16:00, temperature=30.1, humidity_percent=45.1, uv_index=9.0}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-03T18:00, temperature=27.0, humidity_percent=57.0, uv_index=3.8}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=2}, timestamp=2025-06-03T20:00, temperature=23.5, humidity_percent=63.3, uv_index=0.9}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=3}, timestamp=2025-06-03T22:00, temperature=20.8, humidity_percent=69.1, uv_index=0.0}
[CONSUMER] Deserialized: WeatherEvent{station=Station{id=1}, timestamp=2025-06-04T00:00, temperature=18.6, humidity_percent=74.8, uv_index=0.0}
[CONSUMER] Consumed all messages
[MAIN] Done!
```

Next, we'll add transactions to the app leveraging the consumer's database connection. [Proceed to step 3, Transactional Messaging](./transactional-messaging.md).