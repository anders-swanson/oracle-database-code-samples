package com.example.json.analytics;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.sql.SQLException;

@Testcontainers
class OrderAnalyticsSampleTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("schema.sql");

    @Test
    void runAnalyticsDemo() throws SQLException {
        OrderAnalyticsSample sample = new OrderAnalyticsSample();
        try (var connection = sample.createDataSource(
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword()
        ).getConnection()) {
            sample.deleteAll(connection);
            sample.seedSampleData(connection);
            sample.topProducts(connection, 2);
            sample.ordersByRegion(connection, 3);
        }
    }
}
