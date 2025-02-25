package com.example.news.events.producerconsumer;

import java.time.Duration;
import java.util.Collections;

import com.example.news.model.News;
import com.example.news.events.factory.NewsConsumerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NewsConsumer implements Runnable, AutoCloseable {
    private final KafkaConsumer<String, News> consumer;
    private final String newsTopic;


    public NewsConsumer(NewsConsumerFactory newsConsumerFactory,
                        @Value("${news.topic.parsed}") String newsTopic) {
        this.consumer = newsConsumerFactory.create();
        this.newsTopic = newsTopic;
    }


    @Override
    public void run() {
        consumer.subscribe(Collections.singletonList(newsTopic));

        while (true) {
            // Poll a batch of records from the news topic.
            ConsumerRecords<String, News> records = consumer.poll(Duration.ofMillis(200));
            try {
                processRecords(records);
            } catch (Exception e) {
                log.error("Error processing news events", e);
            }
        }
    }

    private void processRecords(ConsumerRecords<String, News> records) {

    }

    @Override
    public void close() throws Exception {
        this.consumer.close();
    }
}
