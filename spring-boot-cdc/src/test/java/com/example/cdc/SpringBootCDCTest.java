package com.example.cdc;

import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertTimeout;

@Testcontainers
@SpringBootTest
public class SpringBootCDCTest {
    /**
     * Use a containerized Oracle Database instance for testing.
     */
    @ServiceConnection
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    /**
     * Set up the test environment:
     * 1. configure Spring Properties to use the test database.
     * 2. run a SQL script to configure the test database for our JMS example.
     */
    @BeforeAll
    static void setUp() throws Exception {
        oracleContainer.start();
        // Configures the test database, granting the test user access to TxEventQ, creating and starting a queue for JMS.
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("init.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
    }


    @Autowired
    private JmsListenerEndpointRegistry jmsListenerEndpointRegistry;

    @Autowired
    TicketService ticketService;

    @Autowired
    CDCConsumer consumer;

    @Test
    void springBootJMSExample() {
        System.out.println("[MAIN] Starting Spring Boot CDC Example");

        for (int i = 0; i < 10; i++) {
            ticketService.createTicket("ticket " + (i+1), "NEW");
        }
        System.out.println("[MAIN] Created tickets");

        System.out.println("[MAIN] Waiting for consumer to finish processing messages...");
        assertTimeout(Duration.ofSeconds(5), () -> {
            consumer.await();
        });

        // Do a clean shutdown of the JMS listener.
        jmsListenerEndpointRegistry.getListenerContainer("cdcConsumer").stop();
        System.out.println("[MAIN] Consumer finished.");
    }
}
