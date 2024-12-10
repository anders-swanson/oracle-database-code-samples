package com.example;

import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertTimeout;

@Testcontainers
@SpringBootTest
public class SpringJmsTest {
    /**
     * Use a containerized Oracle Database instance for testing.
     */
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.5-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword(("testpwd"));

    /**
     * Set up the test envrionment:
     * 1. configure Spring Properties to use the test database.
     * 2. run a SQL script to configure the test database for our JMS example.
     */
    @BeforeAll
    static void setUp() throws Exception {
        oracleContainer.start();

        // Dynamically configure Spring Boot properties to use the Testcontainers database.
        System.setProperty("JDBC_URL", oracleContainer.getJdbcUrl());
        System.setProperty("USERNAME", oracleContainer.getUsername());
        System.setProperty("PASSWORD", oracleContainer.getPassword());

        // Configures the test database, granting the test user access to TxEventQ, creating and starting a queue for JMS.
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("init.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
    }

    @Autowired
    private Producer producer;

    @Autowired
    private Consumer consumer;

    @Autowired
    private JmsListenerEndpointRegistry jmsListenerEndpointRegistry;

    @Test
    void springBootJMSExample() {
        System.out.println("Starting Spring Boot JMS Example");

        for (int i = 1; i < 6; i++) {
            producer.enqueue("test message %d".formatted(i));
        }
        System.out.println("Produced 5 messages to the queue via JMS.");

        System.out.println("Waiting for consumer to finish processing messages...");
        assertTimeout(Duration.ofSeconds(5), () -> {
            consumer.await();
        });

        // Do a clean shutdown of the JMS listener.
        jmsListenerEndpointRegistry.getListenerContainer("sampleConsumer").stop();
        System.out.println("Consumer finished.");
    }
}
