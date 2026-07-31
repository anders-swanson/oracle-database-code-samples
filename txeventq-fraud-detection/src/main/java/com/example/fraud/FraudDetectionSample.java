package com.example.fraud;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

/** Runnable OKafka flow that persists an explainable assessment for each card charge. */
public final class FraudDetectionSample {
    public static void run(Properties baseProperties, List<CardChargeEvent> events) {
        try (ExecutorService executor = newVirtualThreadPerTaskExecutor()) {
            var okafkaConfiguration = new OkafkaConfiguration(baseProperties);

            okafkaConfiguration.createTopic();

            // Start consumer
            var consumer = okafkaConfiguration.createCardTransactionConsumer(events.size());
            executor.submit(consumer);

            // Start producer
            var producer = okafkaConfiguration.createCardTransactionProducer();
            producer.produce(events);

            // Wait for consumer to complete
            // TODO: implement latch or atomic counter
        }
    }
}
