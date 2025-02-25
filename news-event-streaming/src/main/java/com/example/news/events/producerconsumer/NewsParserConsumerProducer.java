package com.example.news.events.producerconsumer;

import java.sql.Connection;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import com.example.news.model.News;
import com.example.news.events.parser.Parser;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;

@Slf4j
public class NewsParserConsumerProducer implements OKafkaTask {
    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, News> producer;
    private final Parser parser;
    private final String rawTopic;
    private final String parsedTopic;

    public NewsParserConsumerProducer(KafkaConsumer<String, String> consumer,
                                      KafkaProducer<String, News> producer,
                                      Parser parser,
                                      String rawTopic,
                                      String parsedTopic) {
        this.consumer = consumer;
        this.producer = producer;
        this.parser = parser;
        this.rawTopic = rawTopic;
        this.parsedTopic = parsedTopic;
    }

    @Override
    public void run() {
        // Subscribe to the news_raw topic, consuming unparsed news events.
        consumer.subscribe(Collections.singletonList(rawTopic));
        while (true) {
            // Poll a batch of records from the news_raw topic.
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            if (records != null && records.count() > 0) {
                processRecords(records);
            }
        }
    }

    public void run() {
        // Subscribe to the news_raw topic, consuming unparsed news events.
        consumer.subscribe(Collections.singletonList("raw_news"));
        while (true) {
            // Poll a batch of records from the news_raw topic.
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            producer.beginTransaction();
            try {
                for (ConsumerRecord<String, String> record : records) {
                    News news = parseRecord(record);
                    ProducerRecord<String, News> pr = new ProducerRecord<>("news", news);
                    producer.send(pr);
                }
                // Commit the consumer/producer
                producer.commitTransaction();
            } catch (Exception e) {
                // Abort both the poll and event publish on an unexpected processing error.
                producer.abortTransaction();
            }
        }
    }

    public void run() {
        // Subscribe to the news_raw topic, consuming unparsed news events.
        consumer.subscribe(Collections.singletonList("news"));
        while (true) {
            // Poll a batch of records from the news_raw topic.
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            // Get the current database connection for the transaction.
            Connection conn = consumer.getDBConnection();
            for (ConsumerRecord<String, String> record : records) {
                // Run database queries for each record.
                processRecord(record, conn);
            }

            // Commit the database transaction for a batch of records
            // May also use commitAsync or auto-commit!
            consumer.commitSync();
        }
    }



    private void processRecords(ConsumerRecords<String, String> records) {
        producer.beginTransaction();
        try {
            for (ConsumerRecord<String, String> record : records) {

            }
            // Process the news events, publishing them to the news topic.
            List<News> newsList = parser.parse(records);
            for (News news : newsList) {
                ProducerRecord<String, News> pr = new ProducerRecord<>(parsedTopic, news);
                producer.send(pr);
            }
            // Commit both the poll and publish of events.
            producer.commitTransaction();
        } catch (Exception e) {
            log.error("Error processing records", e);
            // Abort both the poll and event publish on an unexpected processing error.
            producer.abortTransaction();
        }
    }

    @Override
    public void close() throws Exception {
        this.consumer.close();
        this.producer.close();
    }
}
