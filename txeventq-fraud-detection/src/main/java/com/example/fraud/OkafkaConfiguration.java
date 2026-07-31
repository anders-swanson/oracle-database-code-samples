package com.example.fraud;

import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class OkafkaConfiguration {
    private static final String TOPIC_NAME = "CARD_CHARGES";

    private final Properties baseProperties;
    private final OSONKafkaSerializationFactory serializationFactory;

    public OkafkaConfiguration(Properties baseProperties) {
        JSONB jsonb = JSONB.createDefault();
        this.serializationFactory = new OSONKafkaSerializationFactory(jsonb);
        this.baseProperties = baseProperties;
    }

    public void createTopic() {
        NewTopic newTopic = new NewTopic(TOPIC_NAME, 1, (short) 0);
        try (Admin admin = AdminClient.create(baseProperties)) {
            admin.createTopics(List.of(newTopic)).all().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating " + TOPIC_NAME, e);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Unable to create " + TOPIC_NAME, e);
            }
        }
    }

    public CardTransactionProducer createCardTransactionProducer() {
        return new CardTransactionProducer(
                kafkaProducer(),
                TOPIC_NAME
        );
    }

    public CardTransactionConsumer createCardTransactionConsumer(int expectedEvents) {
        return new CardTransactionConsumer(
                kafkaConsumer(),
                new FraudScoringService(),
                TOPIC_NAME,
                expectedEvents
        );
    }

    public KafkaProducer<String, CardChargeEvent> kafkaProducer() {
        Properties properties = new Properties();
        properties.putAll(baseProperties);
        properties.put("enable.idempotence", "true");
        properties.put("oracle.transactional.producer", "true");
        properties.put("key.serializer", StringSerializer.class.getName());
        return new KafkaProducer<>(properties, new StringSerializer(), serializationFactory.createSerializer());
    }

    public KafkaConsumer<String, CardChargeEvent> kafkaConsumer() {
        Properties properties = new Properties();
        properties.putAll(baseProperties);
        properties.put("group.id", "CARD_FRAUD_DETECTION");
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        properties.put("max.poll.records", "10");
        return new KafkaConsumer<>(properties, new StringDeserializer(), serializationFactory.createDeserializer(CardChargeEvent.class));
    }
}
