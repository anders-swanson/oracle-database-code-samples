package com.example.json.analytics;

import jakarta.json.Json;
import jakarta.json.JsonArray;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Demonstrates SQL/JSON techniques with JSON_TABLE, JSON_EXISTS, and JSON_ARRAYAGG over JDBC.
 */
public class OrderAnalyticsSample {

    private static final String INSERT_ORDER_SQL = "insert into json_orders (order_doc) values (?)";
    private static final String TOP_PRODUCTS_SQL = """
            select jt.product_name, sum(jt.quantity) as total_qty
            from json_orders o
            cross join json_table(
                o.order_doc, '$'
                columns (
                    nested path '$.items[*]' columns (
                        product_name varchar2(100) path '$.name',
                        quantity      number       path '$.qty'
                    )
                )
            ) jt
            group by jt.product_name
            order by total_qty desc
            """;

    private static final String ORDERS_BY_REGION_SQL = """
            select region,
                   json_arrayagg(order_id order by order_id returning clob) as order_ids
            from (
                select o.order_id, regions.region
                from json_orders o,
                     json_table(
                         o.order_doc, '$'
                         columns (
                             region varchar2(50) path '$.shipping.region'
                         )
                     ) regions
                where json_exists(
                    o.order_doc,
                    '$.items[*]?(@.qty > $minQty)'
                    passing ? as "minQty"
                )
            )
            group by region
            order by region
            """;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        OrderAnalyticsSample sample = new OrderAnalyticsSample();
        try (Connection connection = sample.createDataSource(args[0], args[1], args[2]).getConnection()) {
            System.out.println("Applying schema from schema.sql ...");
            sample.applySchema(connection, "/schema.sql");
            System.out.println("Seeding sample orders ...");
            sample.seedSampleData(connection);
            System.out.println("Seeded sample orders.");

            System.out.println("Running topProducts analytics ...");
            Map<String, Number> topProducts = sample.topProducts(connection, 3);
            System.out.println("Top products by quantity: " + topProducts);

            System.out.println("Running ordersByRegion analytics ...");
            Map<String, JsonArray> ordersByRegion = sample.ordersByRegion(connection, 2);
            ordersByRegion.forEach((region, ids) ->
                    System.out.println("Region " + region + " orders: " + ids));
        }
    }

    OracleDataSource createDataSource(String url, String username, String password) throws SQLException {
        OracleDataSource ds = new OracleDataSource();
        ds.setURL(url);
        ds.setUser(username);
        ds.setPassword(password);
        return ds;
    }

    void applySchema(Connection connection, String resourcePath) throws IOException, SQLException {
        try (InputStream stream = OrderAnalyticsSample.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Schema resource not found: " + resourcePath);
            }
            String script = new BufferedReader(new InputStreamReader(stream))
                    .lines()
                    .collect(Collectors.joining("\n"));

            String[] statements = script.split("(?m)^/\\s*$");
            for (String rawStatement : statements) {
                String ddl = rawStatement.trim();
                if (ddl.isEmpty()) {
                    continue;
                }
                if (ddl.endsWith(";")) {
                    ddl = ddl.substring(0, ddl.length() - 1).trim();
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(ddl);
                } catch (SQLException ex) {
                    if (!(ddl.toLowerCase().startsWith("drop") && ex.getErrorCode() == 942)) {
                        throw ex;
                    }
                }
            }
        }
    }

    void seedSampleData(Connection connection) throws SQLException {
        List<JsonObject> orders = List.of(
                Json.createObjectBuilder()
                        .add("orderNumber", "O-1001")
                        .add("shipping", Json.createObjectBuilder()
                                .add("region", "US-East")
                                .add("priority", "standard"))
                        .add("items", Json.createArrayBuilder()
                                .add(item("Keyboard", 3))
                                .add(item("Mouse", 5)))
                        .build(),
                Json.createObjectBuilder()
                        .add("orderNumber", "O-1002")
                        .add("shipping", Json.createObjectBuilder()
                                .add("region", "US-West")
                                .add("priority", "expedite"))
                        .add("items", Json.createArrayBuilder()
                                .add(item("Monitor", 2))
                                .add(item("Mouse", 4))
                                .add(item("Keyboard", 1)))
                        .build(),
                Json.createObjectBuilder()
                        .add("orderNumber", "O-1003")
                        .add("shipping", Json.createObjectBuilder()
                                .add("region", "US-East")
                                .add("priority", "standard"))
                        .add("items", Json.createArrayBuilder()
                                .add(item("Mouse", 7))
                                .add(item("Webcam", 2)))
                        .build()
        );

        try (PreparedStatement ps = connection.prepareStatement(INSERT_ORDER_SQL)) {
            for (JsonObject order : orders) {
                ps.setString(1, order.toString());
                ps.addBatch();
            }
            ps.executeBatch();
            System.out.println("Inserted " + orders.size() + " JSON orders.");
        }
    }

    private JsonObject item(String name, int quantity) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("qty", quantity)
                .build();
    }

    Map<String, Number> topProducts(Connection connection, int limit) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(TOP_PRODUCTS_SQL)) {
            try (ResultSet rs = ps.executeQuery()) {
                LinkedHashMap<String, Number> results = new LinkedHashMap<>();
                while (rs.next() && results.size() < limit) {
                    results.put(rs.getString("product_name"), rs.getLong("total_qty"));
                }
                System.out.println("Computed product totals for " + results.size() + " SKU(s).");
                return results;
            }
        }
    }

    Map<String, JsonArray> ordersByRegion(Connection connection, int minimumQuantity) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(ORDERS_BY_REGION_SQL)) {
            ps.setInt(1, minimumQuantity);
            try (ResultSet rs = ps.executeQuery()) {
                LinkedHashMap<String, JsonArray> results = new LinkedHashMap<>();
                while (rs.next()) {
                    results.put(rs.getString("region"), readJsonArray(rs.getString("order_ids")));
                }
                System.out.println("Aggregated orders by region for " + results.size() + " region(s).");
                return results;
            }
        }
    }

    void deleteAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("truncate table json_orders");
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 942) {
                throw ex;
            }
        }
    }

    private JsonArray readJsonArray(String payload) {
        try (JsonReader reader = Json.createReader(new StringReader(payload))) {
            return reader.readArray();
        }
    }
}
