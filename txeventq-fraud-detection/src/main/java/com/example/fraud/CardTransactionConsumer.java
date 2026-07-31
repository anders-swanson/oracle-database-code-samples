package com.example.fraud;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.oracle.okafka.clients.consumer.KafkaConsumer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;

public class CardTransactionConsumer implements Runnable {
    private final KafkaConsumer<String, CardChargeEvent> consumer;
    private final FraudScoringService scoringService;
    private final String topic;
    private final int expectedEvents;

    public CardTransactionConsumer(KafkaConsumer<String, CardChargeEvent> consumer,
                                   FraudScoringService scoringService,
                                   String topic, int expectedEvents) {
        this.consumer = consumer;
        this.scoringService = scoringService;
        this.topic = topic;
        this.expectedEvents = expectedEvents;
    }


    @Override
    public void run() {
        int consumedEvents = 0;
        consumer.subscribe(Collections.singletonList(topic));
        while (consumedEvents <= expectedEvents) {
            ConsumerRecords<String, CardChargeEvent> poll = consumer.poll(Duration.ofMillis(3000));
            processRecords(poll);
            consumer.commitSync();
            consumedEvents+=poll.count();
        }

        // TODO: send shutdown signal
    }

    private void processRecords(ConsumerRecords<String, CardChargeEvent> poll) {
        Connection conn = consumer.getDBConnection();

        try {
            for (ConsumerRecord<String, CardChargeEvent> record : poll) {
                FraudAssessment assessment = scoringService.score(conn, record.value());
                System.out.printf("%s -> %s (%.1f): %s%n", assessment.transactionId(),
                        assessment.decision(), assessment.totalScore(), assessment.reasonCodes());
            }
        } catch (SQLException e) {
            try {
                System.err.println("Error while processing records: " + e.getMessage());
                conn.rollback();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
