package com.example.text;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;

@Testcontainers
class JdbcOracleTextSampleTest {
    @Container
    static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.3-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withInitScript("schema.sql")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void runsMainAgainstOracleFree() throws Exception {
        JdbcOracleTextSample.main(new String[]{
                oracleContainer.getJdbcUrl(),
                oracleContainer.getUsername(),
                oracleContainer.getPassword()
        });
    }
}
