package com.example.fraud;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

/** Runnable OKafka flow that persists an explainable assessment for each card charge. */
public final class FraudDetectionSample {
    public static void run(Properties baseProperties, List<CardChargeEvent> events) {
        try (ExecutorService executor = newVirtualThreadPerTaskExecutor()) {
            var okafkaConfiguration = new OkafkaConfiguration(baseProperties);

            okafkaConfiguration.createTopic();

            // Start consumer
            CountDownLatch consumerShutdown = new CountDownLatch(1);
            var consumer = okafkaConfiguration.createCardTransactionConsumer(events.size(), consumerShutdown);
            System.out.println("Main: starting consumer");
            Future<?> consumerTask = executor.submit(consumer);

            // Start producer
            var producer = okafkaConfiguration.createCardTransactionProducer();
            System.out.println("Main: starting producer");
            producer.produce(events);

            System.out.println("Main: waiting for consumer");
            awaitConsumer(consumerShutdown, consumerTask);
        }
    }

    private static void awaitConsumer(CountDownLatch consumerShutdown, Future<?> consumerTask) {
        try {
            consumerShutdown.await();
            consumerTask.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the card-transaction consumer", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Card-transaction consumer failed", exception.getCause());
        }
    }
}
