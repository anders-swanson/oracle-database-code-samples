package com.example.spatial;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Struct;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import oracle.spatial.geometry.JGeometry;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

public class JdbcSpatialExample {
    public static final int SRID_WGS84 = 8307;
    public static final double TOLERANCE = 0.005d;

    private static final String TABLE_NAME = "CITY_LANDMARKS";
    private static final String GEOMETRY_COLUMN = "SHAPE";
    private static final String INDEX_NAME = "CITY_LANDMARKS_SHAPE_IDX";

    private final DataSource dataSource;

    public JdbcSpatialExample(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    /**
     * Recreates the sample table, spatial metadata, and spatial index from scratch.
     */
    public void resetSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            dropTableIfPresent(statement);
            statement.execute("""
                    create table %s (
                        id varchar2(36) primary key,
                        name varchar2(100) not null unique,
                        category varchar2(30) not null,
                        shape mdsys.sdo_geometry not null
                    )
                    """.formatted(TABLE_NAME));
            statement.execute("""
                    begin
                        mdsys.sdo_util.insert_sdo_geom_metadata(
                            user,
                            '%s',
                            '%s',
                            mdsys.sdo_dim_array(
                                mdsys.sdo_dim_element('Longitude', -180, 180, 0.005),
                                mdsys.sdo_dim_element('Latitude', -90, 90, 0.005)
                            ),
                            8307
                        );
                    end;
                    """.formatted(TABLE_NAME, GEOMETRY_COLUMN));
            statement.execute("""
                    create index %s
                    on %s(%s)
                    indextype is mdsys.spatial_index_v2
                    """.formatted(INDEX_NAME, TABLE_NAME, GEOMETRY_COLUMN.toLowerCase(Locale.US)));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize the spatial schema", exception);
        }
    }

    /**
     * Loads the sample landmarks and one polygon window used by the queries.
     */
    public void loadSampleData() {
        insertFeature("Ferry Building", "LANDMARK", point(-122.3937, 37.7955));
        insertFeature("Coit Tower", "LANDMARK", point(-122.4058, 37.8024));
        insertFeature("Oracle Park", "LANDMARK", point(-122.3893, 37.7786));
        insertFeature("Golden Gate Bridge", "LANDMARK", point(-122.4783, 37.8199));
        insertFeature("Downtown Window", "AREA", rectangle(-122.4200, 37.7700, -122.3800, 37.8100));
    }

    /**
     * Persists one named spatial feature by converting the JGeometry instance into SDO_GEOMETRY.
     */
    public void insertFeature(String name, String category, JGeometry geometry) {
        String sql = """
                insert into %s (id, name, category, %s)
                values (?, ?, ?, ?)
                """.formatted(TABLE_NAME, GEOMETRY_COLUMN.toLowerCase(Locale.US));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, name);
            statement.setString(3, category);
            statement.setObject(4, JGeometry.storeJS(geometry, connection));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert spatial feature " + name, exception);
        }
    }

    /**
     * Reads one stored SDO_GEOMETRY value and converts it back into JGeometry.
     */
    public JGeometry getGeometry(String name) {
        String sql = """
                select %s
                from %s
                where name = ?
                """.formatted(GEOMETRY_COLUMN.toLowerCase(Locale.US), TABLE_NAME);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("No geometry found for " + name);
                }
                Struct struct = resultSet.getObject(GEOMETRY_COLUMN.toLowerCase(Locale.US), Struct.class);
                return JGeometry.loadJS(struct);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load geometry for " + name, exception);
        }
    }

    /**
     * Returns the minimum bounding rectangle for a stored geometry.
     */
    public double[] getBoundingBox(String name) {
        return getGeometry(name).getMBR();
    }

    /**
     * Uses SDO_FILTER to find landmarks whose geometry overlaps the supplied query window.
     */
    public List<String> findLandmarksInside(JGeometry window) {
        return queryNames("""
                select name
                from %s
                where category = 'LANDMARK'
                  and SDO_FILTER(%s, ?) = 'TRUE'
                order by name
                """.formatted(TABLE_NAME, GEOMETRY_COLUMN.toLowerCase(Locale.US)), window, null);
    }

    /**
     * Uses SDO_WITHIN_DISTANCE to find landmarks within the requested distance in meters.
     */
    public List<String> findLandmarksWithinDistance(JGeometry origin, int distanceInMeters) {
        return queryNames("""
                select name
                from %s
                where category = 'LANDMARK'
                  and SDO_WITHIN_DISTANCE(%s, ?, ?) = 'TRUE'
                order by name
                """.formatted(TABLE_NAME, GEOMETRY_COLUMN.toLowerCase(Locale.US)), origin, "distance=%d unit=M".formatted(distanceInMeters));
    }

    /**
     * Computes the exact distance between two stored features in meters.
     */
    public double distanceBetween(String firstName, String secondName) {
        String sql = """
                select sdo_geom.sdo_distance(a.%s, b.%s, ?, 'unit=M') as distance_m
                from %s a, %s b
                where a.name = ?
                  and b.name = ?
                """.formatted(
                GEOMETRY_COLUMN.toLowerCase(Locale.US),
                GEOMETRY_COLUMN.toLowerCase(Locale.US),
                TABLE_NAME,
                TABLE_NAME
        );
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, TOLERANCE);
            statement.setString(2, firstName);
            statement.setString(3, secondName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Unable to compute distance between the requested features");
                }
                return resultSet.getDouble("distance_m");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to compute distance between features", exception);
        }
    }

    /**
     * Creates a WGS84 point geometry from longitude and latitude ordinates.
     */
    public static JGeometry point(double longitude, double latitude) {
        return JGeometry.createPoint(new double[]{longitude, latitude}, 2, SRID_WGS84);
    }

    /**
     * Creates a closed rectangular polygon in WGS84 coordinates.
     */
    public static JGeometry rectangle(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {
        return JGeometry.createLinearPolygon(new double[]{
                minLongitude, minLatitude,
                maxLongitude, minLatitude,
                maxLongitude, maxLatitude,
                minLongitude, maxLatitude,
                minLongitude, minLatitude
        }, 2, SRID_WGS84);
    }

    public static void main(String[] args) throws SQLException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected arguments: <jdbc-url> <jdbc-user> <jdbc-password>");
        }

        // Build a pooled datasource directly from the command line arguments.
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("SPATIAL_SAMPLE_" + UUID.randomUUID().toString().replace("-", ""));
        dataSource.setURL(args[0]);
        dataSource.setUser(args[1]);
        dataSource.setPassword(args[2]);
        Path diagramOutput = defaultDiagramOutput();

        // Recreate the sample schema and load a small set of landmarks and polygons.
        JdbcSpatialExample sample = new JdbcSpatialExample(dataSource);
        sample.resetSchema();
        sample.loadSampleData();

        // Demonstrate a window query, a within-distance query, a bounding box read, and exact distance calculations.
        List<String> downtownLandmarks = sample.findLandmarksInside(sample.getGeometry("Downtown Window"));
        double[] windowMbr = sample.getBoundingBox("Downtown Window");
        double ferryBuildingToCoitTower = sample.distanceBetween("Ferry Building", "Coit Tower");
        double goldenGateBridgeToDowntownWindow = sample.distanceBetween("Golden Gate Bridge", "Downtown Window");

        System.out.println("Landmarks inside Downtown Window: " + downtownLandmarks);
        System.out.println("Downtown Window MBR: " + format(windowMbr));
        System.out.printf(Locale.US, "Distance from Golden Gate Bridge to Downtown Window: %.2f meters%n", goldenGateBridgeToDowntownWindow);
        System.out.printf(Locale.US, "Distance from Ferry Building to Coit Tower: %.2f meters%n", ferryBuildingToCoitTower);

        // Write an SVG diagram that visualizes the sample geometries and several exact distances.
        try {
            SpatialDiagramGenerator.writeSvg(sample, diagramOutput);
            System.out.println("Spatial diagram written to: " + diagramOutput.toAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write the spatial diagram", exception);
        }
    }

    /**
     * Runs a spatial query that returns landmark names for the supplied geometry and optional parameter string.
     */
    private List<String> queryNames(String sql, JGeometry geometry, String parameters) {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, JGeometry.storeJS(geometry, connection));
            if (parameters != null) {
                statement.setString(2, parameters);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("name"));
                }
            }
            return names;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute spatial query", exception);
        }
    }

    /**
     * Removes the sample table and spatial metadata if they already exist.
     */
    private static void dropTableIfPresent(Statement statement) throws SQLException {
        try {
            statement.execute("drop table " + TABLE_NAME + " purge");
        } catch (SQLException exception) {
            if (exception.getErrorCode() != 942) {
                throw exception;
            }
        }
        statement.execute("""
                begin
                    mdsys.sdo_util.delete_sdo_geom_metadata(user, '%s', '%s');
                exception
                    when others then null;
                end;
                """.formatted(TABLE_NAME, GEOMETRY_COLUMN));
    }

    /**
     * Formats the bounding-box ordinates for console output.
     */
    private static String format(double[] ordinates) {
        return String.format(Locale.US, "[%.4f, %.4f, %.4f, %.4f]",
                ordinates[0], ordinates[1], ordinates[2], ordinates[3]);
    }

    /**
     * Resolves the default diagram output to the jdbc-spatial-example module root.
     */
    static Path defaultDiagramOutput() {
        try {
            Path classesDirectory = Path.of(JdbcSpatialExample.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return classesDirectory.getParent().getParent().resolve("spatial-diagram.svg").normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Unable to determine the module root for the diagram output", exception);
        }
    }
}
