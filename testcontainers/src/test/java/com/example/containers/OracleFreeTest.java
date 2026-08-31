package com.example.containers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class OracleFreeTest {

    static OracleFree oracleContainer = new OracleFree().withInitScript("students.sql");

    static OracleDataSource dataSource;

    @BeforeAll
    static void setUp() throws SQLException {
        oracleContainer.start();

        dataSource = new OracleDataSource();
        dataSource.setURL(oracleContainer.getJdbcUrl());
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
    }

    @Test
    void getConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select first_name from students where email = 'alice.smith@example.edu'")) {
            assertTrue(resultSet.next(), "Expected the initialized application schema to contain a student");
        }
    }
}
