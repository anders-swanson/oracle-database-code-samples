package com.example.jms;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import javax.sql.DataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

@Testcontainers
public class JMSMultiConsumerTest {
    private static final String oracleImage = "gvenzl/oracle-free:23.7-slim-faststart";
    private static final String testUser = "testuser";
    private static final String testPassword = "Welcome123#";

    private static final String topicName = "mytopic";

    @Container
    private static final OracleContainer oracleContainer = new OracleContainer(oracleImage)
            .withStartupTimeout(Duration.ofMinutes(3)) // allow possible slow startup
            .withInitScripts(
                    "create-table.sql"
            )
            .withUsername(testUser)
            .withPassword(testPassword);

    private static DataSource dataSource;

    private static List<String> input;

    @BeforeAll
    static void setUp() throws Exception {
        // Configure the Oracle Database container with the TxEventQ test user.
        oracleContainer.start();
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("testuser.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");

        dataSource = getDataSource();

        input = Files.readAllLines(Paths.get("src", "test", "resources", "producer-events.txt"));
    }

    @Test
    void produceConsume() throws InterruptedException {
        // Used for tracking the number of messages consumed. Once all messages have been consumed and the latch is empty,
        // the test completes.
        CountDownLatch latch = new CountDownLatch(input.size());
        // Number of consumer threads, may be 1 - 6.
        final int consumerThreads = 3;

        // Create an executor to submit producer and consumer threads.
        ExecutorService executor = newVirtualThreadPerTaskExecutor();

        // Start the consumer thread(s).
        for (int i = 0; i < consumerThreads; i++) {
            executor.submit(getConsumer(i+1, latch));
        }

        // Start the producer thread.
        executor.submit(getProducer());

        // Wait for the consumer(s) to receive all messages.
        latch.await();
    }

    private JMSProducer getProducer() {
        return new JMSProducer(
                dataSource,
                testUser,
                topicName,
                input
        );
    }

    private JMSConsumer getConsumer(int id, CountDownLatch latch) {
        return new JMSConsumer(
                dataSource,
                id,
                testUser,
                topicName,
                latch
        );
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
