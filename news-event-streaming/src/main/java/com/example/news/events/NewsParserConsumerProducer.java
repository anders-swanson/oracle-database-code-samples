package com.example.news.events;

import java.sql.Connection;
import java.time.Duration;
import java.util.Collections;

import com.example.news.events.model.News;
import com.example.news.factory.NewsProducerFactory;
import com.example.news.factory.RawConsumerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NewsParserConsumerProducer implements Runnable {
    private final KafkaConsumer<String, String> consumer;
    private final NewsProducerFactory newsProducerFactory;
    private final NewsParser newsParser;
    private final String rawTopic;
    private final String parsedTopic;

    public NewsParserConsumerProducer(RawConsumerFactory rawConsumerFactory,
                                      NewsProducerFactory newsProducerFactory,
                                      NewsParser newsParser,
                                      @Value("${news.topic.raw}") String rawTopic,
                                      @Value("${news.topic.parsed}") String parsedTopic) {
        this.consumer = rawConsumerFactory.create();
        this.newsProducerFactory = newsProducerFactory;
        this.newsParser = newsParser;
        this.rawTopic = rawTopic;
        this.parsedTopic = parsedTopic;
    }

    @Override
    public void run() {
        consumer.subscribe(Collections.singletonList(rawTopic));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            // Retrieve the consumer's database connection. We will use this for the producer,
            // so the consume-produce flow is encapsulated in the same transaction.
            Connection conn = consumer.getDBConnection();
            KafkaProducer<String, News> producer = newsProducerFactory.create(conn);
            try {
                processRecords(producer, records);
            } catch (Exception e) {
                log.error("error processing records", e);
                producer.abortTransaction();
            }
        }
    }

    private void processRecords(KafkaProducer<String, News> producer, ConsumerRecords<String, String> records) {
        if (records != null && records.count() > 0) {
            producer.beginTransaction();
            for (ConsumerRecord<String, String> record : records) {
                News parsed = newsParser.parse(record.value());
                ProducerRecord<String, News> pr = new ProducerRecord<>(parsedTopic, parsed);
                producer.send(pr);
            }
            producer.commitTransaction();
        }
    }
}
