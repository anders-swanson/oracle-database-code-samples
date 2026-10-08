package com.example.metrics;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.example.AdminUtil;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Produces readings every 500 ms and consumes them on the application thread. */
@Component
public class MetricsSample implements ApplicationRunner {
    public static final String TOPIC = "OKAFKA_METRICS_SAMPLE";
    private static final Logger log = LoggerFactory.getLogger(MetricsSample.class);

    private final Properties connectionProperties;
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final EventRecorder eventRecorder;
    private final ObservationRegistry observationRegistry;
    // Identify this run's messages without adding a high-cardinality metric tag.
    private final String runId = UUID.randomUUID().toString();
    private final Random random = new Random();
    private int nextReading;
    private boolean rolledBack;
    private volatile boolean running = true;
    private volatile boolean ready;

    public MetricsSample(Properties okafkaConnectionProperties,
                         KafkaProducer<String, String> metricsProducer,
                         KafkaConsumer<String, String> metricsConsumer,
                         EventRecorder eventRecorder,
                         ObservationRegistry observationRegistry
    ) {
        this.connectionProperties = okafkaConnectionProperties;
        this.producer = metricsProducer;
        this.consumer = metricsConsumer;
        this.eventRecorder = eventRecorder;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            // Create topic in Oracle AI Database
            AdminUtil.createTopicIfNotExists(connectionProperties, new NewTopic(TOPIC, 1, (short) 0));

            // Subscribe to the new topic with the metrics consumer
            consumer.subscribe(List.of(TOPIC));
            ready = true;
            log.info("Transactional readings run ID: {}", runId);

            // Main thread consumes records for the duration of the sample.
            while (running) {
                consume();
            }
        } finally {
            ready = false;
            consumer.close();
        }
    }

    // Every 500ms, records are produced into the sample topic.
    @Scheduled(fixedRate = 500)
    void publishReading() {
        if (!ready) {
            return;
        }
        try {
            // Randomly publish an "abort" event to test consumer transaction & replay
            String reading = random.nextDouble() > 0.9
                    ? "sensor-reading-aborted" : "sensor-reading-" + nextReading++;
            produce(reading);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.error("Failed to publish a sensor reading", exception);
        }
    }

    private void produce(String value) throws Exception {
        var record = new ProducerRecord<>(TOPIC, runId, value);
        // Spring's tracing handler injects context into the Kafka headers on observation start.
        var context = new SenderContext<ProducerRecord<String, String>>((carrier, key, headerValue) ->
                carrier.headers().add(key, headerValue.getBytes(StandardCharsets.UTF_8)));
        context.setCarrier(record);
        Observation.createNotStarted("okafka.produce", () -> context, observationRegistry).observeChecked(() -> {
            producer.beginTransaction();
            try {
                Connection connection = producer.getDBConnection();
                producer.send(record).get(30, TimeUnit.SECONDS);
                // Failure markers have no SQL reading row and can be published repeatedly.
                if (!"sensor-reading-aborted".equals(value)) {
                    eventRecorder.recordProduced(connection, runId, value);
                }
                producer.commitTransaction();
                log.info("Published {}", value);
            } catch (Exception exception) {
                try {
                    producer.abortTransaction();
                } catch (Exception abortFailure) {
                    exception.addSuppressed(abortFailure);
                }
                throw exception;
            }
        });
    }

    private void consume() throws Exception {
        var records = consumer.poll(Duration.ofMillis(500));
        if (records.isEmpty()) {
            return;
        }
        Connection connection = consumer.getDBConnection();
        try {
            int count = 0;
            boolean simulateFailure = false;
            for (var record : records) {
                if (runId.equals(record.key())) {
                    if ("sensor-reading-aborted".equals(record.value())) {
                        simulateFailure = true;
                    } else {
                        // Each record can have a different producer trace, even in the same batch.
                        var context = new ReceiverContext<ConsumerRecord<String, String>>((carrier, key) -> {
                            var header = carrier.headers().lastHeader(key);
                            return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
                        });
                        context.setCarrier(record);
                        Observation.createNotStarted("okafka.process", () -> context, observationRegistry)
                                .observeChecked(() -> {
                                    eventRecorder.recordConsumed(connection, runId, record.value());
                                    log.info("Processed {}", record.value());
                                });
                        count++;
                    }
                }
            }
            // Fail once per batch containing a marker, then acknowledge it on retry.
            if (rolledBack || !simulateFailure) {
                consumer.commitSync();
                rolledBack = false;
                if (count > 0) {
                    log.info("Committed {} consumed readings", count);
                }
            } else {
                connection.rollback();
                rolledBack = true;
                log.info("Rolled back consumed readings; retrying {} records", count);
            }
        } catch (Exception exception) {
            try {
                connection.rollback();
            } catch (Exception rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        ready = false;
    }
}
