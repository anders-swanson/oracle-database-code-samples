package com.example.fraud;

import java.io.File;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FraudDetectionIT {
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "Welcome123#";

    @Container
    private static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withInitScripts("schema.sql", "data.sql")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    private static OracleDataSource dataSource;

    @BeforeAll
    static void setUp() throws Exception {
        oracle.start();
        oracle.copyFileToContainer(MountableFile.forClasspathResource("okafka.sql"), "/tmp/okafka.sql");
        oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/okafka.sql");

        dataSource = new OracleDataSource();
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
    }

    @Test
    void scoresLocalDistantAndUnfamiliarCharges() throws Exception {
        FraudDetectionSample.run(okafkaProperties(), sampleEvents());

        assertAssessment("txn-100", "APPROVE", 0d, 40d, "NORMAL_PATTERN");
        assertAssessment("txn-101", "APPROVE", 0d, 40d, "NORMAL_PATTERN");
        assertAssessment("txn-102", "DECLINE", 70d, 100d, "DISTANT_RECENT_TRANSACTION");
        assertAssessment("txn-103", "REVIEW", 40d, 70d, "UNUSUAL_BEHAVIOR");

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     select json_serialize(raw_event returning clob), spatial_score, behavior_score,
                            amount_score, velocity_score, total_score, decision, reason_codes
                     from card_transactions t join fraud_assessments a on a.transaction_id = t.transaction_id
                     where t.transaction_id = 'txn-102'
                     """)) {
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).contains("txn-102", "TRAVEL", "ECOMMERCE");
            assertThat(result.getDouble(2)).isGreaterThanOrEqualTo(99d);
            assertThat(result.getDouble(3)).isGreaterThan(50d);
            assertThat(result.getDouble(4)).isGreaterThan(50d);
            assertThat(result.getDouble(5)).isZero();
            assertThat(result.getDouble(6)).isGreaterThanOrEqualTo(70d);
            assertThat(result.getString(7)).isEqualTo("DECLINE");
            assertThat(result.getString(8)).contains("DISTANT_RECENT_TRANSACTION");
        }
    }

    private void assertAssessment(String transactionId, String decision, double minimumScore, double maximumScore,
                                  String reasonCode) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     select total_score, decision, reason_codes
                     from fraud_assessments
                     where transaction_id = ?
                     """)) {
            statement.setString(1, transactionId);
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            assertThat(result.getDouble(1)).isBetween(minimumScore, maximumScore);
            assertThat(result.getString(2)).isEqualTo(decision);
            assertThat(result.getString(3)).contains(reasonCode);
        }
    }

    private static Properties okafkaProperties() {
        Properties properties = new Properties();
        properties.put("oracle.service.name", "freepdb1");
        properties.put("security.protocol", "PLAINTEXT");
        properties.put("bootstrap.servers", "localhost:" + oracle.getOraclePort());
        properties.put("oracle.net.tns_admin", new File("src/test/resources").getAbsolutePath());
        return properties;
    }

    private static List<CardChargeEvent> sampleEvents() {
        return List.of(
                new CardChargeEvent("txn-100", "alice", "2026-07-28T10:00:00Z", 62, "USD",
                        "Neighborhood Market", "GROCERY", "CARD_PRESENT", "alice-phone", 37.7955, -122.3937),
                new CardChargeEvent("txn-101", "alice", "2026-07-28T10:30:00Z", 58, "USD",
                        "Neighborhood Market", "GROCERY", "CARD_PRESENT", "alice-phone", 37.7955, -122.3937),
                new CardChargeEvent("txn-102", "alice", "2026-07-28T10:45:00Z", 950, "USD",
                        "Skyline Airways", "TRAVEL", "ECOMMERCE", "unknown-device", 40.7128, -74.0060),
                new CardChargeEvent("txn-103", "bob", "2026-07-28T02:00:00Z", 250, "EUR",
                        "Skyline Airways", "TRAVEL", "ECOMMERCE", "new-device", 37.7955, -122.3937)
        );
    }
}
