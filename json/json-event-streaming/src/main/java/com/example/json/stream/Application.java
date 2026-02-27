package com.example.json.stream;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONSerializer;
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

public class Application {
    private static final String TOPIC_NAME = "json_topic";

    private static final ExecutorService EXECUTOR = newVirtualThreadPerTaskExecutor();

    private static final List<Event> SAMPLE_EVENTS = Arrays.asList(
            new Event("event 1"),
            new Event("event 2"),
            new Event("event 3"),
            new Event("event 4"),
            new Event("event 5")
    );

    private static final int TOTAL_RECORDS = SAMPLE_EVENTS.size();

    private static Properties connectionProperties(String bootstrapServers) {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("security.protocol", "PLAINTEXT");
        // Database service name / TNS Alias
        props.put("oracle.service.name", "freepdb1");
        // Pass directory containing ojdbc.properties file with username/password
        String resourcesDir = new File(Application.class.getClassLoader().getResource("").getFile())
                .getAbsolutePath();
        props.put("oracle.net.tns_admin", resourcesDir);
        return props;
    }
    private static Admin createAdmin(Properties props) {
        return AdminClient.create(props);
    }

    private static Producer<String, Event> createProducer(Properties props) {
        props.put("enable.idempotence", "true");
        JSONB jsonb = JSONB.createDefault();
        Serializer<String> keySerializer = new StringSerializer();
        // Use an OSON (Oracle's binary JSON format) serializer for event data
        Serializer<Event> valueSerializer = new OSONSerializer<>(jsonb);

        return new KafkaProducer<>(props, keySerializer, valueSerializer);
    }

    private static Consumer<String, byte[]> createConsumer(Properties props) {
        props.put("group.id" , "JSON_CONSUMER");
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");
        props.put("max.poll.records", 50);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<>(props);
    }

    private static void createTopic(String boostrapServers) {
        NewTopic testTopic = new NewTopic(TOPIC_NAME, 1, (short) 1);
        try (Admin admin = createAdmin(connectionProperties(boostrapServers))) {
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

    private static Future<?> startProducer(String bootstrapServers) {
        // Start the producer which sends all SAMPLE_EVENTS to the topic
        return EXECUTOR.submit(() -> {
            try (Producer<String, Event> producer = createProducer(connectionProperties(bootstrapServers))) {
                for (Event event : SAMPLE_EVENTS) {
                    ProducerRecord<String, Event> record = new ProducerRecord<>(TOPIC_NAME, event);
                    producer.send(record);
                    System.out.println("[PRODUCER] Serialized: " + event.toString());
                }
            }
            System.out.println("[PRODUCER] Produced all messages");
        });
    }

    private static Future<?> startConsumer(String bootstrapServers) {
        JSONB jsonb = JSONB.createDefault();
        return EXECUTOR.submit(() -> {
            int consumedMessages = 0;
            try (Consumer<String, byte[]> consumer = createConsumer(connectionProperties(bootstrapServers))) {
                consumer.subscribe(List.of(TOPIC_NAME));
                while (consumedMessages < TOTAL_RECORDS) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(3));
                    for (ConsumerRecord<String, byte[]> record : records) {
                        String val = jsonb.fromOSON(record.value(), Event.class)
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
        if  (args.length != 1) {
            System.out.println("Usage: java -jar json-event-stream.jar <bootstrap-servers>");
            System.exit(1);
        }

        String bootstrapServers = args[0];
        System.out.println("Bootstrap servers: " + bootstrapServers);

        createTopic(bootstrapServers);
        System.out.println("[MAIN] Started consumer");
        Future<?> consumerTask = startConsumer(bootstrapServers);
        System.out.println("[MAIN] Started producer");
        Future<?> producerTask = startProducer(bootstrapServers);

        producerTask.get();
        consumerTask.get();
        System.out.println("[MAIN] Done!");
    }

    // JSON DTO event class
    public static class Event {
        private String message;

        // JSON serialization need a default constructor
        public Event() {}

        public Event(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "message='" + message + '\'' +
                    '}';
        }
    }
}
