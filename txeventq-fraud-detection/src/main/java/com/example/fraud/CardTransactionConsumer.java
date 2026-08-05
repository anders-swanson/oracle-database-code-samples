package com.example.fraud;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.oracle.okafka.clients.consumer.KafkaConsumer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.example.fraud.FraudDetectionSample.SELECTAI_ENABLED;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

public class CardTransactionConsumer implements Runnable {
    private static final ExecutorService EXECUTOR = newVirtualThreadPerTaskExecutor();

    private final KafkaConsumer<String, CardChargeEvent> consumer;
    private final FraudScoringService scoringService;
    private final SelectAI selectAI;
    private final String topic;
    private final int expectedEvents;
    private final CountDownLatch shutdownSignal;
    private final DataSource dataSource;
    private final List<Future<?>> selectAIQueries = new ArrayList<>();

    public CardTransactionConsumer(DataSource dataSource, KafkaConsumer<String, CardChargeEvent> consumer,
                                   FraudScoringService scoringService,
                                   SelectAI selectAI,
                                   String topic, int expectedEvents,
                                   CountDownLatch shutdownSignal) {
        this.dataSource = dataSource;
        this.consumer = consumer;
        this.scoringService = scoringService;
        this.selectAI = selectAI;
        this.topic = topic;
        this.expectedEvents = expectedEvents;
        this.shutdownSignal = shutdownSignal;
    }


    @Override
    public void run() {
        int consumedEvents = 0;
        try {
            consumer.subscribe(Collections.singletonList(topic));
            while (consumedEvents < expectedEvents) {
                ConsumerRecords<String, CardChargeEvent> poll = consumer.poll(Duration.ofMillis(3000));
                if (!poll.isEmpty()) {
                    processRecords(poll);
                    consumer.commitSync();
                    System.out.println("Consumer: Processed " + poll.count() + " events");
                    consumedEvents+=poll.count();
                };

            }
        } finally {
            if (SELECTAI_ENABLED) {
                System.out.println("\nWaiting for Select AI Queries to complete...");

                for (Future<?> q : selectAIQueries) {
                    try {
                        q.get(60, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        System.out.println("Consumer: Failed processing select AI query: " + e.getMessage());
                    }
                }
            }

            shutdownSignal.countDown();
        }
    }

    private void processRecords(ConsumerRecords<String, CardChargeEvent> poll) {
        Connection conn = consumer.getDBConnection();

        try {
            for (ConsumerRecord<String, CardChargeEvent> record : poll) {
                if (record.value() == null) {
                    continue;
                }
                CardChargeEvent event = record.value();
                FraudAssessment assessment = scoringService.score(conn, event);
                System.out.printf("\nCARD TRANSACTION ID %d: %s \n-> %s (%.1f): %s%n", event.getTransactionId(), event.toSemanticString(),
                        assessment.decision(), assessment.totalScore(), assessment.reasonCodes());

                if (SELECTAI_ENABLED) {
                    runSelectAI(event);
                }
            }
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                e.addSuppressed(ex);
            }
            throw new IllegalStateException("Unable to score card charge events", e);
        }
    }

    private void runSelectAI(CardChargeEvent event) {
        // while this uses a virtual thread,
        // select ai could also be implemented in another consumer for durability and persistence
        Future<?> query = EXECUTOR.submit(() -> {
            try (Connection conn = dataSource.getConnection()) {
                final String prompt = "give a transaction summary, including an explanation of fraudulent charges for this transaction: %s, Transaction ID %d";
                String result = selectAI.call(conn,
                        prompt.formatted(event.toSemanticString(), event.getTransactionId()),
                        SelectAI.Action.NARRATE);

                System.out.printf("\nSelect AI summary for card transaction %d:\n%s\n", event.getTransactionId(), result);
            } catch (SQLException e) {
                System.err.println("Select AI: Error while running query: " + e.getMessage());
            }
        });

        selectAIQueries.add(query);
    }
}
