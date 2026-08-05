package com.example.fraud;

import java.io.File;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static com.example.fraud.FraudDetectionSample.SELECTAI_ENABLED;
import static com.example.fraud.SelectAISetup.setupWithSelectAI;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FraudDetectionTest {
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "Welcome123#";

    private final static String CERTS_FILE = "https://objectstorage.us-phoenix-1.oraclecloud.com/p/KB63IAuDCGhz_azOVQ07Qa_mxL3bGrFh1dtsltreRJPbmb-VwsH2aQ4Pur2ADBMA/n/adwcdemo/b/CERTS/o/dbc_certs.tar";
    private static final String WALLET_PASSWORD = "MyWalletPassword12345";

    @Container
    private static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withInitScripts("schema.sql")
            .withUsername(USERNAME)
            .withPassword(PASSWORD).withEnv(Map.of(
                    "WALLET_PASSWORD", WALLET_PASSWORD,
                    "CERTS_FILE", CERTS_FILE));

    private static PoolDataSource dataSource;

    @BeforeAll
    static void setUp() throws Exception {
        oracle.start();
        oracle.copyFileToContainer(MountableFile.forClasspathResource("okafka.sql"), "/tmp/okafka.sql");
        oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/okafka.sql");

        dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(10);
        dataSource.setConnectionPoolName("FraudDetectionConsumer");

        try (var connection = dataSource.getConnection()) {
            BehaviorVector.addBehaviorProfile(connection, 1, "local grocery on known phone",
                    sampleEvents().getFirst().toSemanticString());
            BehaviorVector.addBehaviorProfile(connection, 1, "monthly streaming subscription",
                    normalAliceSubscriptionCharge().toSemanticString());
            BehaviorVector.addBehaviorProfile(connection, 2, "local dining on known phone",
                    normalBobCharge().toSemanticString());
            BehaviorVector.addBehaviorProfile(connection, 2, "weekday fuel stop",
                    normalBobFuelCharge().toSemanticString());
        }
        if (SELECTAI_ENABLED) {
            setupWithSelectAI(oracle);
        }
    }

    @Test
    void scoresLocalDistantAndUnfamiliarCharges() throws Exception {
        List<CardChargeEvent> events = sampleEvents();
        FraudDetectionSample.run(dataSource, okafkaProperties(), events);

        CardChargeEvent localCharge = events.get(0);
        CardChargeEvent secondLocalCharge = events.get(1);
        CardChargeEvent distantCharge = events.get(2);
        CardChargeEvent unfamiliarCharge = events.get(3);
        CardChargeEvent subscriptionCharge = events.get(4);
        CardChargeEvent fuelCharge = events.get(5);
        CardChargeEvent cryptoCharge = events.get(6);

        assertThat(events).extracting(CardChargeEvent::getTransactionId)
                .doesNotHaveDuplicates()
                .allMatch(transactionId -> transactionId > 0);

        assertAssessment(localCharge, "APPROVE", 0d, 40d, "NORMAL_PATTERN");
        assertAssessment(secondLocalCharge, "APPROVE", 0d, 40d, "NORMAL_PATTERN");
        assertAssessment(distantCharge, "DECLINE", 70d, 100d, "DISTANT_RECENT_TRANSACTION");
        assertAssessment(unfamiliarCharge, "REVIEW", 40d, 70d, "UNUSUAL_BEHAVIOR");
        assertAssessment(subscriptionCharge, "APPROVE", 0d, 40d, "NORMAL_PATTERN");
        assertAssessment(fuelCharge, "APPROVE", 0d, 40d, "NORMAL_PATTERN");
        assertAssessment(cryptoCharge, "DECLINE", 70d, 100d, "DISTANT_RECENT_TRANSACTION");

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     select t.cardholder_id, t.merchant_name, t.merchant_category, t.channel,
                            a.spatial_score, a.behavior_score, a.amount_score, a.velocity_score,
                            a.total_score, a.decision, a.reason_codes
                     from card_transactions t join fraud_assessments a on a.transaction_id = t.transaction_id
                     where t.transaction_id = ?
                     """)) {
            statement.setLong(1, distantCharge.getTransactionId());
            ResultSet result = statement.executeQuery();
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isEqualTo(distantCharge.getCardholderId());
            assertThat(result.getString(2)).isEqualTo(distantCharge.getMerchantName());
            assertThat(result.getString(3)).isEqualTo(distantCharge.getMerchantCategory());
            assertThat(result.getString(4)).isEqualTo(distantCharge.getChannel());
            assertThat(result.getDouble(5)).isGreaterThanOrEqualTo(99d);
            assertThat(result.getDouble(6)).isGreaterThan(50d);
            assertThat(result.getDouble(7)).isGreaterThan(50d);
            assertThat(result.getDouble(8)).isZero();
            assertThat(result.getDouble(9)).isGreaterThanOrEqualTo(70d);
            assertThat(result.getString(10)).isEqualTo("DECLINE");
            assertThat(result.getString(11)).contains("DISTANT_RECENT_TRANSACTION");
        }
    }

    private void assertAssessment(CardChargeEvent event, String decision, double minimumScore, double maximumScore,
                                  String reasonCode) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     select total_score, decision, reason_codes
                     from fraud_assessments
                     where transaction_id = ?
                     """)) {
            statement.setLong(1, event.getTransactionId());
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
                new CardChargeEvent(100, 1, "2026-07-28T10:00:00Z", 62, "USD",
                        "Neighborhood Market", "GROCERY", "CARD_PRESENT", "alice-phone", 37.7955, -122.3937),
                new CardChargeEvent(101, 1, "2026-07-28T10:30:00Z", 58, "USD",
                        "Neighborhood Market", "GROCERY", "CARD_PRESENT", "alice-phone", 37.7955, -122.3937),
                new CardChargeEvent(102, 1, "2026-07-28T10:45:00Z", 950, "USD",
                        "Skyline Airways", "TRAVEL", "ECOMMERCE", "unknown-device", 40.7128, -74.0060),
                new CardChargeEvent(103, 2, "2026-07-28T02:00:00Z", 250, "EUR",
                        "Skyline Airways", "TRAVEL", "ECOMMERCE", "new-device", 37.7955, -122.3937),
                normalAliceSubscriptionCharge(),
                normalBobFuelCharge(),
                new CardChargeEvent(106, 2, "2026-07-28T07:35:00Z", 2_000, "USD",
                        "Digital Vault Exchange", "CRYPTO", "ECOMMERCE", "new-device", 36.1699, -115.1398)
        );
    }

    private static CardChargeEvent normalAliceSubscriptionCharge() {
        return new CardChargeEvent(104, 1, "2026-07-28T11:05:00Z", 15, "USD",
                "StreamFlix", "ENTERTAINMENT", "ECOMMERCE", "alice-phone", 37.7955, -122.3937);
    }

    private static CardChargeEvent normalBobCharge() {
        return new CardChargeEvent(900, 2, "2026-07-27T12:00:00Z", 50, "USD",
                "Local Cafe", "DINING", "CARD_PRESENT", "bob-phone", 37.7955, -122.3937);
    }

    private static CardChargeEvent normalBobFuelCharge() {
        return new CardChargeEvent(105, 2, "2026-07-28T07:30:00Z", 48, "USD",
                "Bay Fuel", "FUEL", "CARD_PRESENT", "bob-phone", 37.7955, -122.3937);
    }
}
