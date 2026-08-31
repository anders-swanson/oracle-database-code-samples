package com.example.json.multivalue;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Testcontainers
class MultivalueJsonIndexSampleTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withInitScripts("schema.sql", "data.sql")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void runsIndexedLookupDemo() throws Exception {
        MultivalueJsonIndexSample.main(new String[]{
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword()
        });
    }
}
