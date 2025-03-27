package com.example.jdbc.events;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import com.oracle.spring.json.jsonb.JSONB;
import jakarta.json.bind.JsonbBuilder;
import javax.sql.DataSource;
import oracle.sql.json.OracleJsonFactory;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.eclipse.yasson.YassonJsonb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.MountableFile;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
public class JDBCEventStreamingTest {
    private static final String oracleImage = "gvenzl/oracle-free:23.7-slim-faststart";
    private static final String testUser = "testuser";
    private static final String testPassword = "Welcome123#";

    private static final String queueName = "event_stream";

    // For OSON java SerDe.
    private static final JSONB jsonb = new JSONB(
                new OracleJsonFactory(),
                (YassonJsonb) JsonbBuilder.create()
    );

    private static DataSource dataSource;

    private static List<Event> input = new ArrayList<>();

    @Container
    private static final OracleContainer oracleContainer = new OracleContainer(oracleImage)
            .withStartupTimeout(Duration.ofMinutes(3)) // allow possible slow startup
            .withUsername(testUser)
            .withPassword(testPassword);

    @BeforeAll
    static void setUp() throws Exception {
        // Configure the Oracle Database container with the TxEventQ test user.
        oracleContainer.start();
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("jdbc-events.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");

        dataSource = getDataSource();

        ObjectMapper m = new ObjectMapper();
        for (String s : Files.readAllLines(Paths.get("src", "test", "resources", "producer-events.txt"))) {
            Event event = m.readValue(s, Event.class);
            input.add(event);
        }
    }

    @Test
    void produceConsume() throws Exception {
        JDBCBatchProducer producer = new JDBCBatchProducer(
                dataSource,
                jsonb,
                input,
                10
        );

        // Start the JDBC producer.
        ExecutorService executor = newVirtualThreadPerTaskExecutor();
        executor.submit(producer);

        // Start a JDBC consumer group with three consumer threads.
        CountDownLatch latch = new CountDownLatch(input.size());
        final int consumerThreads = 3;
        for (int i = 0; i < consumerThreads; i++) {
            JDBCConsumer consumer = new JDBCConsumer(
                    dataSource,
                    i+1,
                    latch
            );
            executor.submit(consumer);
        }

        // Wait for the consumer group to receive all events.
        latch.await();
        // Verify that all events were sent, processed by consumers,
        // and saved into the weather_events table.
        verifyEventsSent(input.size());
    }

    private void verifyEventsSent(int count) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "select count(*) from weather_events";
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                assertThat(count).isEqualTo(rs.getInt(1));
            } else {
                fail("no records found");
            }
        }
    }

    private static DataSource getDataSource() throws SQLException {
        PoolDataSource ds = PoolDataSourceFactory.getPoolDataSource();
        ds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        ds.setConnectionPoolName(UUID.randomUUID().toString());
        ds.setURL(oracleContainer.getJdbcUrl());
        ds.setUser(oracleContainer.getUsername());
        ds.setPassword(oracleContainer.getPassword());
        ds.setConnectionPoolName(UUID.randomUUID().toString());
        ds.setMaxPoolSize(30);
        ds.setInitialPoolSize(10);
        ds.setMinPoolSize(1);

        return ds;
    }
}
