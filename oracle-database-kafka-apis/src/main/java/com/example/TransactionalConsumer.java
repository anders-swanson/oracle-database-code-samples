package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.oracle.okafka.clients.consumer.KafkaConsumer;

public class TransactionalConsumer implements AutoCloseable, Runnable {
    private static final String insertRecord = """
            insert into records (data, idx) values (?, ?)
            """;

    private final KafkaConsumer<String, String> consumer;
    private final List<String> topics;
    private final int expectedMessages;
    private final boolean isCommitted;

    public TransactionalConsumer(KafkaConsumer<String, String> consumer,
                                 List<String> topics, int expectedMessages,
                                 boolean isCommitted) {
        this.consumer = consumer;
        this.topics = topics;
        this.expectedMessages = expectedMessages;
        this.isCommitted = isCommitted;
    }

    @Override
    public void run() {
        int idx = 0;
        this.consumer.subscribe(topics);
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            System.out.println("Consumed records: " + records.count());
            Connection conn = consumer.getDBConnection();
            for (ConsumerRecord<String, String> record : records) {
                idx++;
                processRecord(record, idx, conn);
            }
            if (!isCommitted) {
                // Since auto-commit is disabled, transactions will not be committed to the DB
                System.out.println("Unexpected error processing records. Aborting transaction!");
                return;
            }
            // Blocking commit on the current batch of records. For non-blocking, use commitAsync()
            consumer.commitSync();
            if (idx >= expectedMessages) {
                System.out.printf("Committed %d records\n", records.count());
                return;
            }
        }
    }

    private void processRecord(ConsumerRecord<String, String> record, int idx, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(insertRecord)) {
            ps.setString(1, record.value());
            ps.setInt(2, idx);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void close() throws Exception {
        if (this.consumer != null) {
            this.consumer.close();
        }
    }
}
