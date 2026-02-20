package com.example.json.jdbc;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import oracle.jdbc.datasource.impl.OracleDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Demonstrates basic CRUD operations using the Oracle JSON data type over JDBC.
 */
public class JsonAttributesSample {

    private static final String INSERT_SQL = "insert into json_products (attributes) values (?)";
    private static final String SELECT_BY_ID_SQL = "select attributes from json_products where id = ?";
    private static final String SELECT_BY_CATEGORY_SQL = "select attributes from json_products where json_value(attributes, '$.category') = ?";
    private static final String UPDATE_PRICE_SQL = "update json_products set attributes = json_transform(attributes, set '$.price' = ? returning json) where id = ?";
    private static final String DELETE_SQL = "delete from json_products where id = ?";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        JsonAttributesSample sample = new JsonAttributesSample();
        try (Connection connection = sample.createDataSource(args[0], args[1], args[2]).getConnection()) {
            sample.applySchema(connection, "/schema.sql");
            sample.runScenario(connection);
        }
    }

    OracleDataSource createDataSource(String url, String user, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        return dataSource;
    }

    void applySchema(Connection connection, String resourcePath) throws IOException, SQLException {
        try (InputStream inputStream = JsonAttributesSample.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Schema resource not found: " + resourcePath);
            }

            String ddl = new BufferedReader(new InputStreamReader(inputStream))
                    .lines()
                    .collect(Collectors.joining("\n"));

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(ddl);
            }
        }
    }

    void runScenario(Connection connection) throws SQLException {
        JsonObject product = Json.createObjectBuilder()
                .add("name", "Oracle Hoodie")
                .add("category", "apparel")
                .add("price", 49.50)
                .add("tags", tags())
                .build();

        long id = insertProduct(connection, product);
        System.out.println("Inserted product id: " + id);

        JsonObject fetched = fetchById(connection, id)
                .orElseThrow(() -> new SQLException("Fetch by id failed"));
        System.out.println("Fetched product: " + fetched);

        JsonObject updated = updatePrice(connection, id, 54.25)
                .orElseThrow(() -> new SQLException("Update price failed"));
        System.out.println("Updated product: " + updated);

        List<JsonObject> apparel = fetchByCategory(connection, "apparel");
        System.out.println("Apparel products: " + apparel.size());

        deleteProduct(connection, id);
        System.out.println("Deleted product id: " + id);
    }

    private JsonArrayBuilder tags() {
        return Json.createArrayBuilder()
                .add("zipper")
                .add("winter")
                .add("blue");
    }

    long insertProduct(Connection connection, JsonObject attributes) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"ID"})) {
            ps.setString(1, attributes.toString());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert JSON document");
    }

    Optional<JsonObject> fetchById(Connection connection, long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readJsonObject(rs.getString(1)));
                }
            }
        }
        return Optional.empty();
    }

    List<JsonObject> fetchByCategory(Connection connection, String category) throws SQLException {
        List<JsonObject> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_CATEGORY_SQL)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(readJsonObject(rs.getString(1)));
                }
            }
        }
        return results;
    }

    Optional<JsonObject> updatePrice(Connection connection, long id, double price) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_PRICE_SQL)) {
            ps.setDouble(1, price);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        return fetchById(connection, id);
    }

    void deleteProduct(Connection connection, long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE_SQL)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private JsonObject readJsonObject(String payload) {
        try (JsonReader reader = Json.createReader(new StringReader(payload))) {
            return reader.readObject();
        }
    }
}
