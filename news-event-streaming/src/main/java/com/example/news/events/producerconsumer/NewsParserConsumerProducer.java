package com.example.news.events.producerconsumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    private void processRecords(ConsumerRecords<String, String> records) {
        producer.beginTransaction();
        try {
            List<CompletableFuture<News>> futures = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                // Asynchronous (with virtual threads) parse each record
                futures.add(parser.parseAsync(record.value()));
            }

            for (CompletableFuture<News> future : futures) {
                // Process the news events, publishing them to the news topic.
                News news = future.get();
                ProducerRecord<String, News> pr = new ProducerRecord<>(
                        parsedTopic,
                        news.get_id(),
                        news
                );
                producer.send(pr);
            }

            // Commit both the poll and publish of events.
            producer.commitTransaction();
            log.info("Successfully processed {} records", records.count());
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
