package com.example;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import oracle.jdbc.pool.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
public class SpringDatabaseResourceTest {
    /**
     * Use a containerized Oracle Database instance for testing.
     */
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.5-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword(("testpwd"));

    /**
     * Dynamically configure Spring Boot properties to use the Testcontainers database.
     */
    @BeforeAll
    static void setUp() throws SQLException {
        oracleContainer.start();
        System.setProperty("JDBC_URL", oracleContainer.getJdbcUrl());
        System.setProperty("USERNAME", oracleContainer.getUsername());
        System.setProperty("PASSWORD", oracleContainer.getPassword());

        // Configure a datasource for the Oracle Database container.
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             PreparedStatement pstmt = conn.prepareStatement("insert into spring_resource (file_name, file_data) values (?, ?)")) {

            // Create a table to for blob storage
            stmt.executeUpdate("""
                create table if not exists spring_resource (
                    file_name  varchar(500) not null primary key,
                    file_data  blob not null
                )""");

            // Populate some initial data to load as Spring Resources
            pstmt.setString(1, "cat.jpg");
            pstmt.setBlob(2, SpringDatabaseResourceTest.class.getClassLoader().getResourceAsStream("cat.jpg"));
            pstmt.executeUpdate();
        }
    }

    @Value("oracledatabase://cat.jpg")
    Resource cat;


    @Test
    void databaseResourceExample() throws IOException {
        // Verifies that Spring Resources are loaded and accessible.
        assertThat(cat.exists()).isTrue();
        assertThat(cat.contentLength()).isEqualTo(new File("src/test/resources/cat.jpg").length());
        assertThat(cat.getInputStream()).isNotNull();
    }
}
