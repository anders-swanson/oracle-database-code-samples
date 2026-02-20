package com.example.json.jdbc;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.sql.SQLException;

@Testcontainers
class JsonAttributesSampleTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.1-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("schema.sql");

    @Test
    void runCrudScenario() throws SQLException {
        JsonAttributesSample sample = new JsonAttributesSample();
        try (var connection = sample.createDataSource(
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword()
        ).getConnection()) {
            sample.runScenario(connection);
        }
    }
}
