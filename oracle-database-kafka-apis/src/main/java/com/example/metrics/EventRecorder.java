package com.example.metrics;

import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

/** SQL work on the connection owned by the active OKafka transaction. */
@Component
public class EventRecorder {
    void recordProduced(Connection connection, String runId, String reading) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into OKAFKA_METRICS_READINGS (run_id, reading) values (?, ?)
                """)) {
            statement.setString(1, runId);
            statement.setString(2, reading);
            statement.executeUpdate();
        }
    }

    void recordConsumed(Connection connection, String runId, String reading) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update OKAFKA_METRICS_READINGS set consumed_at = systimestamp
                where run_id = ? and reading = ?
                """)) {
            statement.setString(1, runId);
            statement.setString(2, reading);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("No produced reading found for " + reading);
            }
        }
    }
}
