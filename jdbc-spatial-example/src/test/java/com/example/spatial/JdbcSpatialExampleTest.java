package com.example.spatial;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcSpatialExampleTest {
    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.3-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void performsBasicSpatialOperations() throws Exception {
        Path diagramFile = JdbcSpatialExample.defaultDiagramOutput();
        Files.deleteIfExists(diagramFile);

        JdbcSpatialExample.main(new String[]{
                oracleContainer.getJdbcUrl(),
                oracleContainer.getUsername(),
                oracleContainer.getPassword()
        });

        JdbcSpatialExample sample = new JdbcSpatialExample(dataSource());
        List<String> downtownLandmarks = sample.findLandmarksInside(sample.getGeometry("Downtown Window"));
        assertThat(downtownLandmarks)
                .containsExactly("Coit Tower", "Ferry Building", "Oracle Park");

        List<String> nearbyLandmarks = sample.findLandmarksWithinDistance(sample.getGeometry("Ferry Building"), 2500);
        assertThat(nearbyLandmarks)
                .containsExactly("Coit Tower", "Ferry Building", "Oracle Park");

        double[] downtownMbr = sample.getBoundingBox("Downtown Window");
        assertThat(downtownMbr).containsExactly(-122.4200, 37.7700, -122.3800, 37.8100);

        double distance = sample.distanceBetween("Ferry Building", "Coit Tower");
        assertThat(distance).isGreaterThan(1000.0d).isLessThan(2500.0d);

        assertThat(diagramFile).exists();
        String svg = Files.readString(diagramFile);
        assertThat(svg).contains("Oracle Spatial Sample Diagram");
        assertThat(svg).contains("Ferry Building");
        assertThat(svg).contains("Coit Tower");
        assertThat(svg).contains("Oracle Park");
        assertThat(svg).contains("Golden Gate Bridge");
        assertThat(svg).contains("Downtown Window");
        assertThat(svg).contains("1312");
    }

    private DataSource dataSource() throws SQLException {
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("SPATIAL_IT_" + UUID.randomUUID().toString().replace("-", ""));
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        return dataSource;
    }
}
