package com.example.jdbc.events;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.oracle.spring.json.jsonb.JSONB;
import javax.sql.DataSource;
import oracle.jdbc.OracleTypes;

public class JDBCBatchProducer implements Runnable {
    private final DataSource dataSource;
    private final JSONB jsonb;
    private final List<Event> input;
    private final int batchSize;


    public JDBCBatchProducer(DataSource dataSource, JSONB jsonb, List<Event> input, int batchSize) {
        this.dataSource = dataSource;
        this.jsonb = jsonb;
        this.input = input;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
        try (Connection conn = dataSource.getConnection();
             CallableStatement cs = conn.prepareCall(
                     "{CALL produce_json_event(?)}"
             )) {

            for (int i = 0; i < input.size(); i++) {
                // Convert java object to OSON.
                byte[] oson = jsonb.toOSON(input.get(i));
                // Set OSON in callable statement.
                cs.setObject(1, oson, OracleTypes.JSON);
                cs.addBatch();

                // Produce a batch of events
                if (i % batchSize == 0) {
                    cs.executeBatch();
                }
            }
            // Produce any remaining events in the batch.
            if (input.size() % batchSize != 0) {
                cs.executeBatch();
            }
        } catch (SQLException e) {
            System.out.println("[PRODUCER] " + e.getMessage());
            throw new RuntimeException(e);
        }
        System.out.println("[PRODUCER] Published all events. Shutting down producer.");
    }
}
