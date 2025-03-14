package com.example;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

public class SysdbaInitTest {
    /**
     * Use a containerized Oracle Database instance for testing.
     */
    @Container
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.7-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    static OracleDataSource ds;

    @BeforeAll
    static void setUp() throws Exception {
        oracleContainer.start();
        // Configure the OracleDataSource to use the database container
        ds = new OracleDataSource();
        ds.setURL(oracleContainer.getJdbcUrl());
        ds.setUser(oracleContainer.getUsername());
        ds.setPassword(oracleContainer.getPassword());

        // Mounts the "dbainit.sql" script on the containerized database, and runs it as syadba.
        // This pattern is useful for configuring user grants or other DBA actions before tests begin
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("dbainit.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
    }

    /**
     * Verifies the containerized database connection.
     * @throws SQLException
     */
    @Test
    void verifyQueue() throws SQLException {
        // Query Database version to verify connection
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("select * from testqueue");
        }
    }
}
