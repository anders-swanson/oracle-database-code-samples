package com.example.clientinfo;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import javax.sql.DataSource;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class ClientInfoApplicationTest {
    private static final String SYS_PASSWORD = "Welcome12345";

    @Container
    @ServiceConnection
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd")
            .withEnv(Map.of(
                    "ORACLE_PASSWORD", SYS_PASSWORD
            ))
            .withInitScript("books.sql");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private BooksController booksController;

    @Test
    void setsClientInfoOnConnections() throws SQLException {
        booksController.createBook(new BooksController.Book(
                1L,
                "Sample Book",
                "Author",
                "ISBN-1234",
                new Date(Instant.now().toEpochMilli())
        ));

        // connect as the system user to view the v$session data
        OracleDataSource inspector = new OracleDataSource();
        inspector.setURL(oracle.getJdbcUrl());
        inspector.setUser("system");
        inspector.setPassword(oracle.getPassword());

        try (Connection connection = inspector.getConnection();
             Statement stmt = connection.createStatement()) {

            final String sql = "select client_identifier, module, action from v$session where username = 'TESTUSER'";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                assertThat(rs.next()).isTrue();
                String clientId = rs.getString("client_identifier");
                String module = rs.getString("module");
                String action = rs.getString("action");
                assertThat(clientId).contains("MyApp@");
                assertThat(module).isEqualTo("Books");
                assertThat(action).isEqualTo("createBook");

                System.out.printf("Client ID: %s, Module: %s, Action: %s\n", clientId, module, action);
            }
        }
    }
}
