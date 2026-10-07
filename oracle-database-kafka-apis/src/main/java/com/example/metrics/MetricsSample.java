package com.example.metrics;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.example.AdminUtil;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Produces and consumes ten messages, leaving the clients open for metric collection. */
@Component
public class MetricsSample implements ApplicationRunner {
    public static final String TOPIC = "OKAFKA_METRICS_SAMPLE";
    public static final int MESSAGE_COUNT = 10;
    private static final Logger log = LoggerFactory.getLogger(MetricsSample.class);

    private final Properties connectionProperties;
    private final Producer<String, String> producer;
    private final Consumer<String, String> consumer;
    private final ObservationRegistry observationRegistry;

    public MetricsSample(Properties okafkaConnectionProperties, Producer<String, String> metricsProducer,
                         Consumer<String, String> metricsConsumer, ObservationRegistry observationRegistry) {
        this.connectionProperties = okafkaConnectionProperties;
        this.producer = metricsProducer;
        this.consumer = metricsConsumer;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Observation.createNotStarted("okafka.sample", observationRegistry).observeChecked(this::runSample);
    }

    private void runSample() throws Exception {
        AdminUtil.createTopicIfNotExists(connectionProperties, new NewTopic(TOPIC, 1, (short) 0));
        consumer.subscribe(List.of(TOPIC));
        // Identify this run's messages without adding a high-cardinality metric tag.
        String runId = UUID.randomUUID().toString();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(new ProducerRecord<>(TOPIC, runId, "sensor-reading-" + i)).get(30, TimeUnit.SECONDS);
        }
        producer.flush();
        log.info("Produced {} sample records", MESSAGE_COUNT);

        int consumed = 0;
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (consumed < MESSAGE_COUNT && System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(500));
            for (var record : records) {
                if (runId.equals(record.key())) {
                    consumed++;
                }
            }
            if (!records.isEmpty()) {
                consumer.commitSync();
            }
        }
        if (consumed != MESSAGE_COUNT) {
            throw new IllegalStateException("Expected " + MESSAGE_COUNT + " sample records, consumed " + consumed);
        }
        log.info("Consumed {} sample records; clients remain open for periodic OTLP metric export", consumed);
    }
}
