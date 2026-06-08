package com.example.graphql;

import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Disabled
@Testcontainers
class JdbcGraphqlSampleTest {
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void graphQLSample() throws Exception {
        JdbcGraphqlSample.main(new String[]{
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword()
        });
    }
}
