package com.example;

import java.time.Duration;
import java.util.List;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class SampleConsumer<T> implements Runnable, AutoCloseable {
    private final Consumer<String, T> consumer;
    private final String topic;
    private final int expectedMessages;

    public SampleConsumer(Consumer<String, T> consumer, String topic, int expectedMessages) {
        this.consumer = consumer;
        this.topic = topic;
        this.expectedMessages = expectedMessages;
    }

    @Override
    public void run() {
        consumer.subscribe(List.of(topic));
        int consumedRecords = 0;
        while (true) {
            ConsumerRecords<String, T> records = consumer.poll(Duration.ofMillis(100));
            System.out.println("Consumed records: " + records.count());
            consumedRecords += records.count();
            if (consumedRecords >= expectedMessages) {
                return;
            }
            processRecords(records);
            // Commit records when done processing.
            consumer.commitAsync();
        }
    }

    private void processRecords(ConsumerRecords<String, T> records) {
        // Application implementation of record processing.
    }

    @Override
    public void close() throws Exception {
        if (consumer != null) {
            consumer.close();
        }
    }
}
