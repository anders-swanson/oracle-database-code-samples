package com.example.uuid.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.example.uuid.JdbcUuidSample.ORDER_ONE_ID;
import static com.example.uuid.JdbcUuidSample.uuidToBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JpaUuidApplication.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JpaUuidSampleTest {
    private static final UUID MISSING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withUsername("testuser")
            .withPassword("testpwd");

    @Autowired
    private JpaUuidSample sample;

    @Autowired
    private JpaOrderRepository orderRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    void storesJpaUuidPrimaryKeysAsRaw16Bytes() throws Exception {
        List<JpaOrder> orders = sample.resetAndLoadSampleData();

        assertEquals(2, orders.size());
        JpaOrder order = orderRepository.findById(ORDER_ONE_ID).orElseThrow();
        assertEquals("ORD-JPA-1001", order.getOrderNumber());
        assertTrue(orderRepository.findById(MISSING_ID).isEmpty());

        assertRaw16Column();
        assertStoredUuidBytes();
    }

    private void assertRaw16Column() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select data_type, data_length
                     from user_tab_columns
                     where table_name = 'JPA_UUID_ORDERS'
                       and column_name = 'ID'
                     """);
             var resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals("RAW", resultSet.getString("data_type"));
            assertEquals(16, resultSet.getInt("data_length"));
        }
    }

    private void assertStoredUuidBytes() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select id
                     from jpa_uuid_orders
                     where order_number = ?
                     """)) {
            statement.setString(1, "ORD-JPA-1001");
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                byte[] storedId = resultSet.getBytes("id");
                assertEquals(16, storedId.length);
                assertArrayEquals(uuidToBytes(ORDER_ONE_ID), storedId);
            }
        }
    }
}
