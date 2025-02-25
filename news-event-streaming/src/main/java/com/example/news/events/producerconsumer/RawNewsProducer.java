package com.example.news.events.producerconsumer;

import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.producer.KafkaProducer;

public class RawNewsProducer implements AutoCloseable {
    private final String rawTopic;
    private final KafkaProducer<String, String> producer;


    public RawNewsProducer(
            String rawTopic,
            KafkaProducer<String, String> producer) {
        this.rawTopic = rawTopic;
        this.producer = producer;
    }

    public void send(List<String> news) {
        try {
            for (String newsItem : news) {
                // Write data to the raws new topic, adding to the transaction.
                ProducerRecord<String, String> record = new ProducerRecord<>(rawTopic, newsItem);
                producer.send(record);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        this.producer.close();
    }
}
