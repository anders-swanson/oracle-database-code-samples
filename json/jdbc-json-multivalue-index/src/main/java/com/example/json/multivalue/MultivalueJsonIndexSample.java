package com.example.json.multivalue;

import oracle.jdbc.datasource.impl.OracleDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates multivalue function-based indexes over scalar values inside JSON arrays.
 */
public class MultivalueJsonIndexSample {

    private static final int DEMO_PART_ID = 102;
    private static final String DEMO_REGION = "US-WEST";
    private static final String DEMO_COMPONENT_TYPE = "port";
    private static final int DEMO_COMPONENT_PART_ID = 203;

    private static final String FIND_PRODUCTS_BY_PART_SQL = """
            select json_value(product_doc, '$.sku' returning varchar2(40)) as sku,
                   json_value(product_doc, '$.name' returning varchar2(100)) as name
            from json_inventory
            where json_exists(
                product_doc,
                '$.compatiblePartIds?(@.numberOnly() == $partId)'
                passing ? as "partId"
            )
            order by sku
            """;
    private static final String FIND_PRODUCTS_BY_REGION_SQL = """
            select json_value(product_doc, '$.sku' returning varchar2(40)) as sku,
                   json_value(product_doc, '$.name' returning varchar2(100)) as name
            from json_inventory
            where json_exists(
                product_doc,
                '$.warehouses.region?(@.stringOnly() == $region)'
                passing ? as "region"
            )
            order by sku
            """;
    private static final String FIND_PRODUCTS_BY_COMPONENT_SQL = """
            select json_value(product_doc, '$.sku' returning varchar2(40)) as sku,
                   json_value(product_doc, '$.name' returning varchar2(100)) as name
            from json_inventory
            where json_exists(
                product_doc,
                '$.components[*]?(@.type == $componentType && @.partId == $partId)'
                passing ? as "componentType", ? as "partId"
            )
            order by sku
            """;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <jdbcUrl> <username> <password>");
            System.exit(1);
        }

        MultivalueJsonIndexSample sample = new MultivalueJsonIndexSample();
        try (Connection connection = sample.createDataSource(args[0], args[1], args[2]).getConnection()) {
            System.out.println("Connecting to DB: " + args[0]);
            sample.printResults(connection);
        }
    }

    void printResults(Connection connection) throws SQLException {
        List<ProductMatch> productsByPart = findProductsByCompatiblePart(connection, DEMO_PART_ID);
        List<ProductMatch> productsByRegion = findProductsByWarehouseRegion(connection, DEMO_REGION);
        List<ProductMatch> productsByComponent = findProductsByComponent(connection, DEMO_COMPONENT_TYPE, DEMO_COMPONENT_PART_ID);

        System.out.println("Products compatible with part " + DEMO_PART_ID + ": " + productsByPart);
        System.out.println("Products stocked in " + DEMO_REGION + ": " + productsByRegion);
        System.out.println("Products with " + DEMO_COMPONENT_TYPE + " component " + DEMO_COMPONENT_PART_ID + ": " + productsByComponent);
    }

    OracleDataSource createDataSource(String url, String username, String password) throws SQLException {
        OracleDataSource ds = new OracleDataSource();
        ds.setURL(url);
        ds.setUser(username);
        ds.setPassword(password);
        return ds;
    }

    List<ProductMatch> findProductsByCompatiblePart(Connection connection, int partId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(FIND_PRODUCTS_BY_PART_SQL)) {
            ps.setInt(1, partId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductMatch> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new ProductMatch(rs.getString("sku"), rs.getString("name")));
                }
                return results;
            }
        }
    }

    List<ProductMatch> findProductsByWarehouseRegion(Connection connection, String region) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(FIND_PRODUCTS_BY_REGION_SQL)) {
            ps.setString(1, region);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductMatch> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new ProductMatch(rs.getString("sku"), rs.getString("name")));
                }
                return results;
            }
        }
    }

    List<ProductMatch> findProductsByComponent(Connection connection, String componentType, int partId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(FIND_PRODUCTS_BY_COMPONENT_SQL)) {
            ps.setString(1, componentType);
            ps.setInt(2, partId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductMatch> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new ProductMatch(rs.getString("sku"), rs.getString("name")));
                }
                return results;
            }
        }
    }

    public record ProductMatch(String sku, String name) {
    }
}
