package com.example.kafka2;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.oracle.spring.json.jsonb.JSONB;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

public class KafkaApp {
    private static final String TOPIC_NAME = "test_topic";
    private static final int TOTAL_RECORDS = 10;
    private static final ExecutorService EXECUTOR = newVirtualThreadPerTaskExecutor();

    private static Properties connectionProperties() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "localhost:1521");
        props.setProperty("security.protocol", "PLAINTEXT");
        // Database service name / TNS Alias
        props.put("oracle.service.name", "freepdb1");
        // Pass directory containing ojdbc.properties file with username/password
        String resourcesDir = new File(KafkaApp.class.getClassLoader().getResource("").getFile())
                .getAbsolutePath();
        props.put("oracle.net.tns_admin", resourcesDir);
        return props;
    }
    private static Admin createAdmin(Properties props) {
        return AdminClient.create(props);
    }

    private static Producer<String, WeatherEvent> createProducer(Properties props) {
        props.put("enable.idempotence", "true");
        JSONB jsonb = JSONB.createDefault();
        Serializer<String> keySerializer = new StringSerializer();
        Serializer<WeatherEvent> valueSerializer = new OSONSerializer<>(jsonb);

        return new KafkaProducer<>(props, keySerializer, valueSerializer);
    }

    private static Consumer<String, byte[]> createConsumer(Properties props) {
        props.put("group.id" , "MY_CONSUMER_GROUP");
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");
        props.put("max.poll.records", 50);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<>(props);
    }

    private static void createTopic() {
        NewTopic testTopic = new NewTopic(TOPIC_NAME, 1, (short) 1);
        try (Admin admin = createAdmin(connectionProperties())) {
            admin.createTopics(List.of(testTopic))
                    .all()
                    .get();
            System.out.println("[ADMIN] Created topic: " + testTopic.name());
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                System.out.println("[ADMIN] Topic already exists");
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private static Future<?> startProducer() {
        // Start the producer, which sends 10 total messages (value of TOTAL_RECORDS).
        return EXECUTOR.submit(() -> {
            try (Producer<String, WeatherEvent> producer = createProducer(connectionProperties())) {
                for (WeatherEvent event : WeatherEvent.getSampleEvents()) {
                    ProducerRecord<String, WeatherEvent> record = new ProducerRecord<>(TOPIC_NAME, event);
                    producer.send(record);
                    System.out.println("[PRODUCER] Serialized: " + event.toString());
                }
            }
            System.out.println("[PRODUCER] Produced all messages");
        });
    }

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

    public static void main(String... args) throws Exception {
        createTopic();
        System.out.println("[MAIN] Started consumer");
        Future<?> consumerTask = startConsumer();
        System.out.println("[MAIN] Started producer");
        Future<?> producerTask = startProducer();

        producerTask.get();
        consumerTask.get();
        System.out.println("[MAIN] Done!");
    }
}
