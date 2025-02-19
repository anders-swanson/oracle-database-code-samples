package com.example.news.factory;

import java.sql.Connection;
import java.util.Properties;

import com.example.news.events.model.News;
import com.example.news.events.serde.JSONBDeserializer;
import com.example.news.events.serde.JSONBSerializer;
import com.oracle.spring.json.jsonb.JSONB;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class NewsConsumerFactory {
    private final Properties okafkaProperties;
    private final JSONB jsonb;

    public NewsConsumerFactory(@Qualifier("okafkaProperties") Properties okafkaProperties,
                               JSONB jsonb) {
        this.okafkaProperties = okafkaProperties;
        this.jsonb = jsonb;
    }

    public KafkaConsumer<String, News> create(Connection connection) {
        Properties props = new Properties();
        props.putAll(okafkaProperties);
        props.put("auto.offset.reset", "earliest");
        props.put("group.id" , "NEWS_CONSUMER");
        props.put("enable.auto.commit","false");
        props.put("max.poll.records", 50);

        Deserializer<String> keyDeserializer = new StringDeserializer();
        Deserializer<News> valueDeserializer = new JSONBDeserializer<>(jsonb, News.class);
        return new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
    }
}
