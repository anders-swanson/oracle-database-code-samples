package com.example.fraud;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

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
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;

/** Runnable OKafka flow that persists an explainable assessment for each card charge. */
public final class FraudDetectionSample {
    public static final String TOPIC_NAME = "CARD_CHARGES";

    private FraudDetectionSample() {
    }

    public static void run(Properties baseProperties, List<CardChargeEvent> events) throws Exception {
        createTopic(baseProperties);
        produce(baseProperties, events);
        consumeAndScore(baseProperties, events.size());
    }

    public static void createTopic(Properties baseProperties) {
        NewTopic topic = new NewTopic(TOPIC_NAME, 1, (short) 0);
        try (Admin admin = AdminClient.create(copy(baseProperties))) {
            admin.createTopics(List.of(topic)).all().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating " + TOPIC_NAME, exception);
        } catch (ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Unable to create " + TOPIC_NAME, exception);
            }
        }
    }

    private static void produce(Properties baseProperties, List<CardChargeEvent> events) throws Exception {
        Properties properties = copy(baseProperties);
        properties.put("enable.idempotence", "true");
        JSONB jsonb = JSONB.createDefault();
        try (Producer<String, CardChargeEvent> producer = new KafkaProducer<>(properties,
                new StringSerializer(), new OSONSerializer<>(jsonb))) {
            for (CardChargeEvent event : events) {
                producer.send(new ProducerRecord<>(TOPIC_NAME, event.getTransactionId(), event)).get();
            }
        }
    }

    private static void consumeAndScore(Properties baseProperties, int expectedEvents) throws Exception {
        Properties properties = copy(baseProperties);
        properties.put("group.id", "FRAUD_SCORER");
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        JSONB jsonb = JSONB.createDefault();
        int processed = 0;
        try (Consumer<String, byte[]> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC_NAME));
            try (var connection = ((KafkaConsumer<String, byte[]>) consumer).getDBConnection()) {
                FraudScoringService scoringService = new FraudScoringService();
                while (processed < expectedEvents) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(5));
                    if (records.isEmpty()) {
                        continue;
                    }
                    for (ConsumerRecord<String, byte[]> record : records) {
                        CardChargeEvent event = deserialize(jsonb, record.value());
                        FraudAssessment assessment = scoringService.score(connection, event);
                        System.out.printf("%s -> %s (%.1f): %s%n", assessment.transactionId(),
                                assessment.decision(), assessment.totalScore(), assessment.reasonCodes());
                        processed++;
                    }
                    connection.commit();
                    consumer.commitSync();
                }
            }
        }
    }

    private static CardChargeEvent deserialize(JSONB jsonb, byte[] payload) {
        try {
            return jsonb.fromOSON(payload, CardChargeEvent.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read card charge event", exception);
        }
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }
}
