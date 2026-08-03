package com.example.fraud;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.oracle.okafka.clients.consumer.KafkaConsumer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import static com.example.fraud.FraudDetectionSample.SELECTAI_ENABLED;

public class CardTransactionConsumer implements Runnable {
    private final KafkaConsumer<String, CardChargeEvent> consumer;
    private final FraudScoringService scoringService;
    private final SelectAI selectAI;
    private final String topic;
    private final int expectedEvents;
    private final CountDownLatch shutdownSignal;

    public CardTransactionConsumer(KafkaConsumer<String, CardChargeEvent> consumer,
                                   FraudScoringService scoringService,
                                   SelectAI selectAI,
                                   String topic, int expectedEvents,
                                   CountDownLatch shutdownSignal) {
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
                System.out.printf("\nCARD TRANSACTION: %s \n-> %s (%.1f): %s%n", event.toSemanticString(),
                        assessment.decision(), assessment.totalScore(), assessment.reasonCodes());

                if (SELECTAI_ENABLED) {
                    final String prompt = "give a transaction summary, including an explanation of fraudulent charges for this transaction: %s, Transaction ID %d";
                    String result = selectAI.call(conn,
                            prompt.formatted(event.toSemanticString(), event.getTransactionId()),
                            SelectAI.Action.NARRATE);

                    System.out.printf("Select AI summary for transaction:\n%s\n", result);
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
}
