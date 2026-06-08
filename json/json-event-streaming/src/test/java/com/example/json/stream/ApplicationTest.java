package com.example.json.stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

@Testcontainers
public class ApplicationTest {
    // Pre-pull this image to avoid testcontainers image pull timeouts:
    // docker pull gvenzl/oracle-free:23.26.2-slim-faststart
    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.26.2-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd");

    @BeforeAll
    static void applyGrants() throws Exception {
        // Configure the Oracle AI Database container with the TxEventQ test user for OKafka
        // see src/test/resources/init.sql
        oracleContainer.start();
        oracleContainer.copyFileToContainer(MountableFile.forClasspathResource("init.sql"), "/tmp/init.sql");
        oracleContainer.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/init.sql");
    }

    @Test
    public void runApplication() throws Exception {
        Integer port = oracleContainer.getOraclePort();
        String host = oracleContainer.getHost();
        String bootstrapServers = String.format("%s:%d", host, port);

        Application.main(bootstrapServers);
    }

}
