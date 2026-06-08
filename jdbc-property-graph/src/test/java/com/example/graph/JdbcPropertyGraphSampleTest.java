package com.example.graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPropertyGraphSampleTest {
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    @BeforeAll
    static void setUp() throws Exception {
        oracleContainer.start();
        oracleContainer.copyFileToContainer(
                MountableFile.forClasspathResource("grant-property-graph.sql"),
                "/tmp/grant-property-graph.sql"
        );
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/grant-property-graph.sql");
    }

    @AfterAll
    static void tearDown() {
        oracleContainer.stop();
    }

    @Test
    void runsTheSampleAgainstOracleDatabaseFree() throws Exception {
        Path diagramFile = JdbcPropertyGraphSample.defaultDiagramOutput();
        Files.deleteIfExists(diagramFile);

        JdbcPropertyGraphSample.main(new String[]{
                oracleContainer.getJdbcUrl(),
                oracleContainer.getUsername(),
                oracleContainer.getPassword()
        });

        JdbcPropertyGraphSample sample = new JdbcPropertyGraphSample(dataSource());

        List<String> directFriends = sample.listDirectFriends("Alice");
        assertThat(directFriends).containsExactly("Bob", "Cara");

        List<String> twoHopFriends = sample.listFriendsWithinTwoHops("Alice");
        assertThat(twoHopFriends).containsExactly("Bob", "Cara", "Diego", "Emma");

        List<String> recommendations = sample.listRecommendedFriends("Alice");
        assertThat(recommendations).containsExactly("Emma", "Diego");

        assertThat(diagramFile).exists();
        String svg = Files.readString(diagramFile);
        assertThat(svg).contains("Oracle Property Graph Sample Diagram");
        assertThat(svg).contains("Alice");
        assertThat(svg).contains("Bob");
        assertThat(svg).contains("Cara");
        assertThat(svg).contains("Diego");
        assertThat(svg).contains("Emma");
        assertThat(svg).contains("since 2021 | strength 9");
        assertThat(svg).contains("Recommended friends:");
    }

    private DataSource dataSource() throws SQLException {
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("PROPERTY_GRAPH_IT_" + UUID.randomUUID().toString().replace("-", ""));
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        return dataSource;
    }
}
