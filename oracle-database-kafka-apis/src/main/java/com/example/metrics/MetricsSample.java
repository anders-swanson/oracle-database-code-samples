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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Verifies ten messages at startup, then publishes and consumes a reading every 500 ms. */
@Component
public class MetricsSample implements ApplicationRunner {
    public static final String TOPIC = "OKAFKA_METRICS_SAMPLE";
    public static final int MESSAGE_COUNT = 10;
    private static final Logger log = LoggerFactory.getLogger(MetricsSample.class);

    private final Properties connectionProperties;
    private final Producer<String, String> producer;
    private final Consumer<String, String> consumer;
    private final ObservationRegistry observationRegistry;
    // Identify this run's messages without adding a high-cardinality metric tag.
    private final String runId = UUID.randomUUID().toString();
    private int nextReading = MESSAGE_COUNT;
    private volatile boolean ready;

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
        ready = true;
    }

    @Scheduled(fixedRate = 500)
    void publishReading() {
        if (!ready) {
            return;
        }
        try {
            Observation.createNotStarted("okafka.reading", observationRegistry).observeChecked(() -> {
                producer.send(new ProducerRecord<>(TOPIC, runId, "sensor-reading-" + nextReading++))
                        .get(30, TimeUnit.SECONDS);
                var records = consumer.poll(Duration.ofMillis(100));
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
                log.info("Published a sensor reading; consumed {} records", records.count());
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.error("Failed to publish or consume a sensor reading", exception);
        }
    }

    private void runSample() throws Exception {
        AdminUtil.createTopicIfNotExists(connectionProperties, new NewTopic(TOPIC, 1, (short) 0));
        consumer.subscribe(List.of(TOPIC));
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
