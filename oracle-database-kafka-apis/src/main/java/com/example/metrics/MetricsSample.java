package com.example.metrics;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.example.AdminUtil;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.NewTopic;
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
    private final Tracer tracer;
    // Identify this run's messages without adding a high-cardinality metric tag.
    private final String runId = UUID.randomUUID().toString();
    private int nextReading;
    private boolean rolledBack;
    private volatile boolean running = true;
    private volatile boolean ready;

    public MetricsSample(Properties okafkaConnectionProperties,
                         KafkaProducer<String, String> metricsProducer,
                         KafkaConsumer<String, String> metricsConsumer,
                         EventRecorder eventRecorder,
                         ObservationRegistry observationRegistry,
                         Tracer tracer
    ) {
        this.connectionProperties = okafkaConnectionProperties;
        this.producer = metricsProducer;
        this.consumer = metricsConsumer;
        this.eventRecorder = eventRecorder;
        this.observationRegistry = observationRegistry;
        this.tracer = tracer;
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
                Observation.createNotStarted("okafka.sample", observationRegistry).observeChecked(this::consume);
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
            Observation.createNotStarted("okafka.reading", observationRegistry).observeChecked(() -> {
                if (nextReading == 0) {
                    produce("sensor-reading-aborted", false);
                    log.info("Aborted producer transaction");
                }
                produce("sensor-reading-" + nextReading++, true);
                log.info("Published a sensor reading");
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.error("Failed to publish a sensor reading", exception);
        }
    }

    private void produce(String value, boolean commit) throws Exception {
        var span = tracer.nextSpan().name("okafka.producer.transaction").start();
        try (var ignored = tracer.withSpan(span)) {
            producer.beginTransaction();
            try {
                Connection connection = producer.getDBConnection();
                producer.send(new ProducerRecord<>(TOPIC, runId, value)).get(30, TimeUnit.SECONDS);
                eventRecorder.recordProduced(connection, runId, value);
                if (commit) {
                    producer.commitTransaction();
                } else {
                    producer.abortTransaction();
                }
                span.tag("outcome", commit ? "committed" : "aborted");
            } catch (Exception exception) {
                try {
                    producer.abortTransaction();
                } catch (Exception abortFailure) {
                    exception.addSuppressed(abortFailure);
                }
                throw exception;
            }
        } catch (Exception exception) {
            span.tag("outcome", "failed").error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private void consume() throws Exception {
        var span = tracer.nextSpan().name("okafka.consumer.transaction").start();
        try (var ignored = tracer.withSpan(span)) {
            var records = consumer.poll(Duration.ofMillis(500));
            if (records.isEmpty()) {
                span.tag("outcome", "empty");
                return;
            }
            Connection connection = consumer.getDBConnection();
            try {
                int count = 0;
                for (var record : records) {
                    if (runId.equals(record.key())) {
                        if ("sensor-reading-aborted".equals(record.value())) {
                            throw new IllegalStateException("Received a record from an aborted transaction");
                        }
                        eventRecorder.recordConsumed(connection, runId, record.value());
                        count++;
                    }
                }
                if (rolledBack || count == 0) {
                    consumer.commitSync();
                    span.tag("outcome", "committed");
                    if (count > 0) {
                        log.info("Committed {} consumed readings", count);
                    }
                } else {
                    connection.rollback();
                    span.tag("outcome", "rolled_back");
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
        } catch (Exception exception) {
            span.tag("outcome", "failed").error(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        ready = false;
    }
}
