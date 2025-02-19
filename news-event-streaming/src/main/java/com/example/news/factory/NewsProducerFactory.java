package com.example.news.factory;

import java.sql.Connection;
import java.util.Properties;

import com.example.news.events.model.News;
import com.example.news.events.serde.JSONBSerializer;
import com.oracle.spring.json.jsonb.JSONB;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class NewsProducerFactory {
    private final Properties okafkaProperties;
    private final JSONB jsonb;

    public NewsProducerFactory(@Qualifier("okafkaProperties") Properties okafkaProperties,
                               JSONB jsonb) {
        this.okafkaProperties = okafkaProperties;
        this.jsonb = jsonb;
    }

    public KafkaProducer<String, News> create(Connection connection) {
        Properties props = new Properties();
        props.putAll(okafkaProperties);
        props.put("enable.idempotence", "true");
        // This property is required for transactional producers
        props.put("oracle.transactional.producer", "true");

        Serializer<String> keySerializer = new StringSerializer();
        Serializer<News> valueSerializer = new JSONBSerializer<>(jsonb);
        KafkaProducer<String, News> producer = new KafkaProducer<>(props, keySerializer, valueSerializer, connection);
        producer.initTransactions();
        return producer;
    }
}
