package com.example.uuid;

import oracle.jdbc.datasource.impl.OracleDataSource;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

public class JdbcUuidSample {
    public static final UUID ORDER_ONE_ID = UUID.fromString("2f4b6f9a-1d7e-4c6b-8d4a-2c8e5f9b0a11");
    public static final UUID ORDER_TWO_ID = UUID.fromString("6c2f4a91-b03d-469d-ae13-0c0d73513a4e");

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final String TABLE_NAME = "UUID_ORDERS";
    private static final String INSERT_SQL = """
            insert into uuid_orders (id, order_number, customer_name, total_amount)
            values (?, ?, ?, ?)
            """;
    private static final String FIND_BY_ID_SQL = """
            select id, order_number, customer_name, total_amount
            from uuid_orders
            where id = ?
            """;

    private final DataSource dataSource;

    public JdbcUuidSample(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        JdbcUuidSample sample = new JdbcUuidSample(createDataSource(args[0], args[1], args[2]));
        sample.resetSchema();
        sample.loadSampleData();

        System.out.println("Stored Java UUID primary keys as RAW(16):");
        for (OrderRow order : sample.findAllOrders()) {
            System.out.printf(
                    "%s | bytes=%s | order=%s | customer=%s | total=%s%n",
                    order.id(),
                    HEX.formatHex(uuidToBytes(order.id())),
                    order.orderNumber(),
                    order.customerName(),
                    order.totalAmount().setScale(2)
            );
        }
    }

    public static OracleDataSource createDataSource(String url, String username, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    public void resetSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            dropTableIfPresent(statement);
            statement.execute("""
                    create table uuid_orders (
                        id raw(16) primary key,
                        order_number varchar2(40) not null unique,
                        customer_name varchar2(100) not null,
                        total_amount number(10,2) not null
                    )
                    """);
        }
    }

    public void loadSampleData() throws SQLException {
        insertOrder(new OrderRow(ORDER_ONE_ID, "ORD-1001", "Avery Stone", new BigDecimal("42.50")));
        insertOrder(new OrderRow(ORDER_TWO_ID, "ORD-1002", "Mina Rao", new BigDecimal("125.00")));
    }

    public void insertOrder(OrderRow order) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setBytes(1, uuidToBytes(order.id()));
            statement.setString(2, order.orderNumber());
            statement.setString(3, order.customerName());
            statement.setBigDecimal(4, order.totalAmount());
            statement.executeUpdate();
        }
    }

    public Optional<OrderRow> findOrder(UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setBytes(1, uuidToBytes(id));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readOrder(resultSet));
            }
        }
    }

    public List<OrderRow> findAllOrders() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select id, order_number, customer_name, total_amount
                     from uuid_orders
                     order by order_number
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            List<OrderRow> orders = new ArrayList<>();
            while (resultSet.next()) {
                orders.add(readOrder(resultSet));
            }
            return orders;
        }
    }

    public static byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    public static UUID bytesToUuid(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Expected 16 bytes for a UUID but found " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static OrderRow readOrder(ResultSet resultSet) throws SQLException {
        return new OrderRow(
                bytesToUuid(resultSet.getBytes("id")),
                resultSet.getString("order_number"),
                resultSet.getString("customer_name"),
                resultSet.getBigDecimal("total_amount")
        );
    }

    private static void dropTableIfPresent(Statement statement) throws SQLException {
        try {
            statement.execute("drop table " + TABLE_NAME + " purge");
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 942) {
                throw exception;
            }
        }
    }

    public record OrderRow(UUID id, String orderNumber, String customerName, BigDecimal totalAmount) {
    }
}
