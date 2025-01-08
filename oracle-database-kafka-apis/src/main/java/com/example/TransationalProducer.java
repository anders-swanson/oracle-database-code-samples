package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.producer.KafkaProducer;

/**
 * TransactionalProducer uses the org.oracle.okafka.clients.producer.KafkaProducer database connection
 * to write records to a table.
 *
 * After limit records have been produced, the TransactionalProducer simulates a processing error,
 * and aborts the current producer batch.
 */
public class TransationalProducer implements AutoCloseable {
    private static final String insertRecord = """
            insert into records (data, idx) values (?, ?)
            """;

    private final KafkaProducer<String, String> producer;
    private final String topic;

    // Simulate an message processing error after limit messages have been produced
    private final int limit;

    public TransationalProducer(KafkaProducer<String, String> producer,
                                String topic,
                                int limit) {
        this.producer = producer;
        this.topic = topic;
        this.limit = limit;
        // Initialize transactional producer
        this.producer.initTransactions();
    }

    public void produce(Stream<String> inputs) {
        // Being producer transaction
        producer.beginTransaction();
        Connection conn = producer.getDBConnection();
        int idx = 0;
        Iterator<String> records = inputs.iterator();
        while (records.hasNext()) {
            String record = records.next();
            if (++idx >= limit) {
                System.out.printf("Produced %d records\n", idx);
                System.out.println("Unexpected error processing records. Aborting transaction!");
                // Abort the database transaction on error, cancelling the effective batch
                producer.abortTransaction();
                return;
            }

            ProducerRecord<String, String> pr = new ProducerRecord<>(topic, Integer.toString(idx), record);
            producer.send(pr);
            persistRecord(record, idx, conn);
        }
        System.out.printf("Produced %d records\n", idx);
        // commit the transaction
        producer.commitTransaction();
    }

    private void persistRecord(String record, int idx, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(insertRecord)) {
            ps.setString(1, record);
            ps.setInt(2, idx);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (this.producer != null) {
            producer.close();
        }
    }
}
