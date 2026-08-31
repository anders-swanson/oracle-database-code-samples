package com.example.jdv.crud;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.sql.SQLException;

/**
 * This test provides the option to run the main app with a containerized test database.
 * The test database is created in the context of the test and deleted afterwards.
 */
@Testcontainers
public class JDVCrudTest {
    // Pre-pull this image to avoid testcontainers image pull timeouts:
    // docker pull gvenzl/oracle-free:23.26.3-slim-faststart
    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("init.sql");


    @Test
    public void runAppSample() throws SQLException {
        Application.main(
                new String[]{
                        oracleContainer.getJdbcUrl(),
                        oracleContainer.getUsername(),
                        oracleContainer.getPassword()
                }
        );
    }

}
