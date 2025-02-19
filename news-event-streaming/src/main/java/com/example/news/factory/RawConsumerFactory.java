package com.example.news.factory;

import java.util.Properties;

import com.oracle.spring.json.jsonb.JSONB;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RawConsumerFactory {
    private final Properties okafkaProperties;

    public RawConsumerFactory(@Qualifier("okafkaProperties") Properties okafkaProperties) {
        this.okafkaProperties = okafkaProperties;
    }

    public KafkaConsumer<String, String> create() {
        Properties props = new Properties();
        props.putAll(okafkaProperties);
        // Note the use of standard Kafka properties for OKafka configuration.
        props.put("group.id" , "RAW_NEWS_CONSUMER");
        props.put("enable.auto.commit","false");
        props.put("max.poll.records", 50);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");

        return new KafkaConsumer<>(props);
    }
}
