package com.example.news.events;

import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RawNewsProducer {
    private final String rawNewsTopic;
    private final KafkaProducer<String, String> producer;


    public RawNewsProducer(
            @Value("${news.topic.raw}") String rawNewsTopic,
            @Qualifier("stringProducer") KafkaProducer<String, String> producer) {
        this.rawNewsTopic = rawNewsTopic;
        this.producer = producer;
    }

    public void send(List<String> news) {
        // Begin a new producer transaction.
        producer.beginTransaction();
        try {
            for (String newsItem : news) {
                // Write data to the raws new topic, adding to the transaction.
                ProducerRecord<String, String> record = new ProducerRecord<>(rawNewsTopic, newsItem);
                producer.send(record);
            }
            // Commit each record in the raws news data transaction.
            producer.commitTransaction();
        } catch (Exception e) {
            // On error, abort the transaction.
            producer.abortTransaction();
            throw new RuntimeException(e);
        }
    }
}
