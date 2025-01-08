package com.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
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
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionalConsumeIT {
    // Oracle Databse 23ai Free container image
    private static final String oracleImage = "gvenzl/oracle-free:23.5-slim-faststart";
    private static final String testUser = "testuser";
    private static final String testPassword = "Welcome123#";
    private final String topicName = "TEST";

    private static OracleDataSource dataSource;

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

        // Create test table for Transactional Producer
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {
            stmt.executeQuery("""
                create table records (
                    id   varchar(36) default sys_guid() primary key,
                    data varchar(255),
                    idx  number
                )
                """);
        }
    }

    @Container
    private static final OracleContainer oracleContainer = new OracleContainer(oracleImage)
            .withStartupTimeout(Duration.ofMinutes(3)) // allow possible slow startup
            .withUsername(testUser)
            .withPassword(testPassword);

    @Test
    void transactionalConsume() throws Exception {
        // Create a topic to produce messages to, and consume messages from.
        // This topic will have 1 partition and a replication factor of 0,
        // since we are testing locally with a containerized database.
        NewTopic topic = new NewTopic(topicName, 1, (short) 0);
        AdminUtil.createTopicIfNotExists(getOKafkaConnectionProperties(), topic);
        // Add records to the topic.
        produceRecords();
        // Consume records and simulate a crash. No records should be committed.
        doTransactionalConsume(false);
        // Consume records, without any crash. All records should be committed.
        doTransactionalConsume(true);
    }

    private void doTransactionalConsume(boolean isCommitted) throws Exception {
        // Create the Consumer.
        Properties consumerProps = getOKafkaConnectionProperties();
        consumerProps.put("group.id" , "MY_CONSUMER_GROUP");
        consumerProps.put("enable.auto.commit","false");
        consumerProps.put("max.poll.records", 2000);
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> okafkaConsumer = new KafkaConsumer<>(consumerProps);

        System.out.printf("#### Starting TransactionalConsumer (isCommitted=%B) ####%n", isCommitted);

        // Run the TransactionalConsumer
        try (TransactionalConsumer consumer = new TransactionalConsumer(
                okafkaConsumer,
                List.of(topicName),
                50,
                isCommitted
        )) {
            consumer.run();
        }

        // Query the database, and verify the records table
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("""
                        select * from records
                    """);
            assertThat(resultSet.next()).isEqualTo(isCommitted);
            System.out.println("#### Verified committed records status ####");
            System.out.printf("#### TransactionalConsumer completed (isCommitted=%B) ####%n", isCommitted);
        }
    }

    private void produceRecords() throws Exception {
        Properties producerProps = getOKafkaConnectionProperties();
        producerProps.put("enable.idempotence", "true");
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> okafkaProducer = new KafkaProducer<>(producerProps);

        try (SampleProducer<String> sampleProducer = new SampleProducer<>(okafkaProducer, topicName, getDataStream())) {
            sampleProducer.run();
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
