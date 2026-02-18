package com.example.jdv.crud;

import jakarta.json.stream.JsonParser;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.pool.OracleDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Application {
    private static final String INSERT_ORDER_PRODUCT = """
            insert into orders_dv (data) values (?)
            returning json_value(data, '$._id' returning number) into ?
            """;

    private static final String SELECT_ORDER_BY_ID = """
            select * from orders_dv dv
            where dv.data."_id" = ?
            """;

    private static final String UPDATE_ORDER_QUANTITY =
            """
            update orders_dv dv
            set dv.data = json_transform(dv.data, set '$.quantity' = ?)
            where dv.data."_id" = ?
    """;

    private static final String DELETE_ORDER_BY_ID = """
            delete from orders_dv dv
            where dv.data."_id" = ?
            """;


    public static void main(String[] args) throws SQLException {
        if (args.length != 3) {
            System.out.println("Usage: <JDBC URL> <Username> <Password>");
            System.exit(1);
        }
        System.out.println("Starting JDV Crud Application");

        OSONMapper oson = OSONMapper.createDefault();

        OracleDataSource ds = new OracleDataSource();
        ds.setURL(args[0]);
        ds.setUser(args[1]);
        ds.setPassword(args[2]);

        System.out.println("Connecting to DB: " + args[0]);

        long generatedId;
        try (Connection conn = ds.getConnection();
             OraclePreparedStatement ps = (OraclePreparedStatement) conn.prepareStatement(INSERT_ORDER_PRODUCT)) {
            Product product = new Product();
            product.setName("my product");
            product.setPrice(100.00);

            Order order = new Order();
            order.setProduct(product);
            order.setQuantity(10);

            // Serialize to OSON
            byte[] data = oson.toOSON(order);
            ps.setObject(1, data, OracleTypes.JSON);
            // Register the RETURNING bind (2nd bind)
            ps.registerReturnParameter(2, OracleTypes.NUMBER);
            ps.executeUpdate();

            // Get returned JSON document
            try (ResultSet rs = ps.getReturnResultSet()) {
                if (rs.next()) {
                    generatedId = rs.getLong(1);
                } else {
                    throw new SQLException("Insert failed: no returned document obtained.");
                }
            }
        }

        Order created = new Order();
        System.out.println("Created order ID: " + generatedId);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ORDER_BY_ID)) {
            ps.setLong(1, generatedId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                JsonParser parser = rs.getObject(1, JsonParser.class);
                created = oson.fromOSON(parser, Order.class);
                System.out.println("Retrieved created order by id: " + created);
            }
        }

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_ORDER_QUANTITY)) {
            ps.setLong(1, 15);
            ps.setLong(2, created.getId());
            ps.executeUpdate();
        }

        System.out.println("Updated order ID: " + created.getId());

        try (Connection conn = ds.getConnection();
            PreparedStatement ps = conn.prepareStatement(DELETE_ORDER_BY_ID)) {
            ps.setLong(1, created.getId());
            ps.executeUpdate();
        }

        System.out.println("Deleted order ID: " + created.getId());
    }
}
