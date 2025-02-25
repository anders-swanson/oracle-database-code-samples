package com.example.news.events.factory;

import java.util.Properties;

import com.example.news.model.News;
import com.example.news.events.serde.JSONBDeserializer;
import com.oracle.spring.json.jsonb.JSONB;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
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

    public KafkaConsumer<String, News> create() {
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
