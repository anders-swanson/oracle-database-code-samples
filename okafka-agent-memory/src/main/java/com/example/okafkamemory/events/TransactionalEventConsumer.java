package com.example.okafkamemory.events;

import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** One thread owns consumption, database writes, next-stage publication, and transaction completion. */
public class TransactionalEventConsumer<T> implements SmartLifecycle {
    @FunctionalInterface
    public interface Handler<T> {
        void handle(Connection connection, ConsumerRecord<String, T> record, EventPublisher publisher) throws Exception;
    }

    private static final Logger logger = LoggerFactory.getLogger(TransactionalEventConsumer.class);
    private final String topic;
    private final KafkaConsumer<String, T> consumer;
    private final Properties producerProperties = new Properties();
    private final Handler<T> handler;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicLong processedRecords = new AtomicLong();
    private volatile boolean running;
    private volatile Throwable failure;
    private Thread thread;

    public TransactionalEventConsumer(String topic, KafkaConsumer<String, T> consumer,
                                      Properties properties, Handler<T> handler) {
        this.topic = topic;
        this.consumer = consumer;
        this.handler = handler;
        producerProperties.putAll(properties);
        producerProperties.put("oracle.transactional.producer", "true");
        producerProperties.put("enable.idempotence", "true");
        producerProperties.put("client.id", "memory-" + topic);
    }

    @Override
    public synchronized void start() {
        if (thread != null) return;
        running = true;
        thread = Thread.ofPlatform().name("memory-" + topic).start(this::poll);
    }

    private void poll() {
        KafkaProducer<String, Object> producer = null;
        Connection connection = null;
        try {
            consumer.subscribe(List.of(topic));
            while (running) {
                var records = consumer.poll(Duration.ofMillis(250));
                if (records.isEmpty()) continue;
                Connection current = consumer.getDBConnection();
                if (producer == null || current != connection) {
                    if (producer != null) producer.close();
                    connection = current;
                    producer = new KafkaProducer<>(producerProperties, new StringSerializer(),
                            new OSONKafkaSerializationFactory(JSONB.createDefault()).createSerializer(), connection);
                    producer.initTransactions();
                }
                KafkaProducer<String, Object> transactionalProducer = producer;
                producer.beginTransaction();
                try {
                    for (var record : records) {
                        handler.handle(connection, record, (nextTopic, key, event) ->
                                transactionalProducer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                                        nextTopic, key, event)).get());
                    }
                    // Commits consumption, relational writes, and next-stage events on the same connection.
                    producer.commitTransaction();
                    processedRecords.addAndGet(records.count());
                    failure = null;
                } catch (Exception error) {
                    try {
                        producer.abortTransaction();
                    } catch (RuntimeException rollbackFailure) {
                        error.addSuppressed(rollbackFailure);
                        throw error;
                    }
                    failure = error;
                    logger.warn("Stage {} rolled back; its events will be redelivered", topic, error);
                    if (error instanceof InterruptedException) throw error;
                    Thread.sleep(1000);
                }
            }
        } catch (Exception error) {
            failure = error;
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.error("Stage {} stopped with uncommitted events", topic, error);
        } finally {
            try {
                if (producer != null) producer.close();
            } finally {
                try {
                    consumer.close();
                } finally {
                    running = false;
                    stopped.countDown();
                }
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        if (thread == null) {
            consumer.close();
            return;
        }
        try {
            if (!stopped.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out stopping stage " + topic);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted stopping stage " + topic, interrupted);
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public Throwable failure() {
        return failure;
    }

    public long processedRecords() {
        return processedRecords.get();
    }
}
