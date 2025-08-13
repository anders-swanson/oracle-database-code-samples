package com.example;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class InitializedDatabaseTest {
    /**
     * Use a containerized Oracle Database instance for testing.
     */
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("students.sql");

    static OracleDataSource ds;

    @BeforeAll
    static void setUp() throws SQLException {
        oracleContainer.start();
        // Configure the OracleDataSource to use the database container
        ds = new OracleDataSource();
        ds.setURL(oracleContainer.getJdbcUrl());
        ds.setUser(oracleContainer.getUsername());
        ds.setPassword(oracleContainer.getPassword());
    }

    /**
     * Verifies the database is initialized with a student
     * @throws SQLException
     */
    @Test
    void getStudent() throws SQLException {
        // Query Database version to verify connection
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select * from students where first_name = 'Alice'")) {
            Assertions.assertTrue(rs.next());
            assertThat(rs.getString(2)).isEqualTo("Alice");
        }
    }
}
