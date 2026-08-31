package com.example.txn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

class SessionlessTransactionsTest {

    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withInitScript("orders.sql");

    private static OracleDataSource dataSource;

    @BeforeAll
    static void startContainer() throws SQLException {
        oracleContainer.start();
        dataSource = new OracleDataSource();
        dataSource.setURL(oracleContainer.getJdbcUrl());
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
    }

    @AfterAll
    static void stopContainer() {
        oracleContainer.stop();
    }

    @Test
    void processesOrderAcrossSessions() throws Exception {
        OrderService orderService = new OrderService(dataSource);
        System.out.println("beginning order processing");
        orderService.processOrder();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "select status from order_processing order by id")) {
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("created");
                assertThat(rs.next()).isTrue();
                String nextStatus = rs.getString("status");
                if (nextStatus.equals("inventory_reserved")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("completed");
                    System.out.println("processed order");
                } else {
                    assertThat(rs.getString("status")).isEqualTo("failed");
                    assertThat(rs.next()).isFalse();
                    System.out.println("failed order");
                }
            }
        }
    }
}
