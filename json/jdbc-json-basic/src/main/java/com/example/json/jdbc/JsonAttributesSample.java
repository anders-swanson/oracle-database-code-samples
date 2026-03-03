package com.example.json.jdbc;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.stream.JsonParser;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.datasource.impl.OracleDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Demonstrates basic CRUD operations using the Oracle JSON data type over JDBC with OSON payloads.
 */
public class JsonAttributesSample {

    private static final String INSERT_SQL = "insert into json_products (attributes) values (?)";
    private static final String SELECT_BY_ID_SQL = "select attributes from json_products where id = ?";
    private static final String SELECT_BY_CATEGORY_SQL = "select attributes from json_products where json_value(attributes, '$.category') = ?";
    private static final String UPDATE_PRICE_SQL = "update json_products set attributes = json_transform(attributes, set '$.price' = ? returning json) where id = ?";
    private static final String DELETE_SQL = "delete from json_products where id = ?";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(args[0]);
        dataSource.setUser(args[1]);
        dataSource.setPassword(args[2]);

        OSONMapper oson = OSONMapper.createDefault();

        System.out.println("Connecting to DB: " + args[0]);

        try (Connection connection = dataSource.getConnection()) {
            try (InputStream inputStream = JsonAttributesSample.class.getResourceAsStream("/schema.sql")) {
                if (inputStream == null) {
                    throw new IOException("Schema resource not found: /schema.sql");
                }
                String ddl = new BufferedReader(new InputStreamReader(inputStream))
                        .lines()
                        .collect(java.util.stream.Collectors.joining("\n"));
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(ddl);
                }
            }

            ProductAttributes product = new ProductAttributes();
            product.setName("Oracle Hoodie");
            product.setCategory("apparel");
            product.setPrice(49.50);
            product.setTags(List.of("zipper", "winter", "blue"));

            long id;
            try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"ID"})) {
                OraclePreparedStatement oraclePreparedStatement = ps.unwrap(OraclePreparedStatement.class);
                oraclePreparedStatement.setObject(1, oson.toOSON(product), OracleTypes.JSON);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getLong(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated id for inserted product");
                    }
                }
            }

            System.out.println("Inserted product id: " + id + ", product: " + product);

            ProductAttributes fetchedProduct;
            try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        JsonParser parser = rs.getObject(1, JsonParser.class);
                        fetchedProduct = oson.fromOSON(parser, ProductAttributes.class);
                    } else {
                        throw new SQLException("No product found by id after insert");
                    }
                }
            }

            System.out.println("Fetched product: " + fetchedProduct);

            try (PreparedStatement ps = connection.prepareStatement(UPDATE_PRICE_SQL)) {
                ps.setDouble(1, 54.25);
                ps.setLong(2, id);
                ps.executeUpdate();
            }

            ProductAttributes updatedProduct;
            try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        JsonParser parser = rs.getObject(1, JsonParser.class);
                        updatedProduct = oson.fromOSON(parser, ProductAttributes.class);
                    } else {
                        throw new SQLException("No product found by id after update");
                    }
                }
            }

            System.out.println("Updated product: " + updatedProduct);

            List<ProductAttributes> apparelProducts = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_CATEGORY_SQL)) {
                ps.setString(1, "apparel");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JsonParser parser = rs.getObject(1, JsonParser.class);
                        apparelProducts.add(oson.fromOSON(parser, ProductAttributes.class));
                    }
                }
            }

            System.out.println("Apparel products: " + apparelProducts.size());

            try (PreparedStatement ps = connection.prepareStatement(DELETE_SQL)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }

            System.out.println("Deleted product id: " + id);
        }
    }
}
