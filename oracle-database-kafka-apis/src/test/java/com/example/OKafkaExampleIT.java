package com.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import oracle.jdbc.pool.OracleDataSource;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Running this test requires a local environment capable of running containers.
 * I suggest pre-pulling the gvenzl/oracle-free:23.5-slim-faststart image for
 * fast test startup.
 */
@Testcontainers
public class OKafkaExampleIT {
    // Oracle Databse 23ai Free container image
    private static final String oracleImage = "gvenzl/oracle-free:23.5-slim-faststart";
    private static final String testUser = "testuser";
    private static final String testPassword = "Welcome123#";
    private final String topicName = "TXEVENTQ_EXAMPLE";

    private static OracleDataSource dataSource;

    @Container
    private static final OracleContainer oracleContainer = new OracleContainer(oracleImage)
            .withStartupTimeout(Duration.ofMinutes(3)) // allow possible slow startup
            .withUsername(testUser)
            .withPassword(testPassword);

    @BeforeAll
    static void setUp() throws Exception {
        // Configure the Oracle Database container with the TxEventQ test user.
        oracleContainer.start();
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("okafka.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");

        // Configure a datasource for the Oracle Database container.
        // The datasource is used to demonstrate TxEventQ table duality.
        dataSource = new OracleDataSource();
        dataSource.setUser(testUser);
        dataSource.setPassword(testPassword);
        dataSource.setURL(oracleContainer.getJdbcUrl());
    }

    @Test
    void producerConsumerExample() throws Exception {
        // Create a topic to produce messages to, and consume messages from.
        // This topic will have 1 partition and a replication factor of 0,
        // since we are testing locally with a containerized database.
        NewTopic topic = new NewTopic(topicName, 1, (short) 0);
        AdminUtil.createTopicIfNotExists(getOKafkaConnectionProperties(), topic);

        // Create the OKafka Producer.
        Properties producerProps = getOKafkaConnectionProperties();
        producerProps.put("enable.idempotence", "true");
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> okafkaProducer = new KafkaProducer<>(producerProps);

        // Create the OKafka Consumer.
        Properties consumerProps = getOKafkaConnectionProperties();
        consumerProps.put("group.id" , "MY_CONSUMER_GROUP");
        consumerProps.put("enable.auto.commit","false");
        consumerProps.put("max.poll.records", 2000);
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        Consumer<String, String> okafkaConsumer = new KafkaConsumer<>(consumerProps);

        // Launch the sample producer and consumer asynchronously,
        // and wait for their completion.
        try (SampleProducer<String> sampleProducer = new SampleProducer<>(okafkaProducer, topicName, getDataStream());
                SampleConsumer<String> sampleConsumer = new SampleConsumer<>(okafkaConsumer, topicName, 50);
                ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> producerFuture = executor.submit(sampleProducer);
            Future<?> consumerFuture = executor.submit(sampleConsumer);
            producerFuture.get();
            consumerFuture.get();
        }

        System.out.println("#### Consumer/Producer completed ####");

        // Because the records produced by the OKafka Producer lands in the database,
        // we can easily query this using SQL.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("""
                        select * from TXEVENTQ_EXAMPLE
                        fetch first 1 row only
                    """);
            // Verify that data is present in the TxEventQ table
            assertThat(resultSet.next()).isTrue();
            ResultSetMetaData metaData = resultSet.getMetaData();
            System.out.println("#### TxEventQ Columns: ####");
            metaData.getColumnCount();
            for (int i = 0; i < metaData.getColumnCount(); i++) {
                System.out.println(metaData.getColumnName(i + 1));
            }
        }
    }

    private Properties getOKafkaConnectionProperties() {
        String ojbdcFilePath = new File("src/test/resources").getAbsolutePath();
        return OKafkaProperties.getLocalConnectionProps(ojbdcFilePath, oracleContainer.getOraclePort());
    }

    private Stream<String> getDataStream() throws IOException {
        return Files.lines(new File("src/test/resources/weather_sensor_data.txt").toPath());
    }
}
