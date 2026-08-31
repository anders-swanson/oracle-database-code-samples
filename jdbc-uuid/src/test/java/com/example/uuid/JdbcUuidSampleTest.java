package com.example.uuid;

import oracle.jdbc.datasource.impl.OracleDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;

import static com.example.uuid.JdbcUuidSample.ORDER_ONE_ID;
import static com.example.uuid.JdbcUuidSample.ORDER_TWO_ID;
import static com.example.uuid.JdbcUuidSample.bytesToUuid;
import static com.example.uuid.JdbcUuidSample.uuidToBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class JdbcUuidSampleTest {
    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final UUID MISSING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void storesUuidPrimaryKeysAsRaw16Bytes() throws Exception {
        JdbcUuidSample.main(connectionArgs());

        JdbcUuidSample sample = new JdbcUuidSample(dataSource());
        JdbcUuidSample.OrderRow order = sample.findOrder(ORDER_ONE_ID).orElseThrow();
        assertEquals("ORD-1001", order.orderNumber());

        assertTrue(sample.findOrder(MISSING_ID).isEmpty());

        try (Connection connection = dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select id
                     from uuid_orders
                     where order_number = ?
                     """)) {
            statement.setString(1, "ORD-1001");
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                byte[] storedId = resultSet.getBytes("id");
                assertEquals(16, storedId.length);
                assertArrayEquals(uuidToBytes(ORDER_ONE_ID), storedId);
                assertEquals(ORDER_ONE_ID, bytesToUuid(storedId));
            }
        }
    }

    @Test
    void uuidConversionUsesJavaBitOrder() {
        assertEquals(
                "2F4B6F9A1D7E4C6B8D4A2C8E5F9B0A11",
                rawHex(ORDER_ONE_ID)
        );
        assertEquals(
                "6C2F4A91B03D469DAE130C0D73513A4E",
                rawHex(ORDER_TWO_ID)
        );
    }

    private static OracleDataSource dataSource() throws SQLException {
        return JdbcUuidSample.createDataSource(
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword()
        );
    }

    private static String[] connectionArgs() {
        return new String[]{
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword()
        };
    }

    private static String rawHex(UUID uuid) {
        return HEX.formatHex(uuidToBytes(uuid));
    }
}
