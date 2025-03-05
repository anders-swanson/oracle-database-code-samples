package com.example.news;

import javax.sql.DataSource;
import java.sql.SQLException;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest
@Testcontainers
// This integration test uses OCI GenAI services.
// To run this test, set the following environment variables using your OCI compartment,
// and Cohere model IDs for chat and embedding.
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_CHAT_MODEL_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_EMBEDDING_MODEL_ID", matches = ".+")
public class NewsEventStreamingIT {
    private final String compartmentId = System.getenv("OCI_COMPARTMENT");
    // You can find your model id in the OCI Console.
    private final String chatModelId = System.getenv("OCI_CHAT_MODEL_ID");
    // https://smith.langchain.com/hub/rlm/rag-prompt
    private final String promptTemplate = """
            You are an assistant for question-answering tasks. Use the following pieces of retrieved context to answer the question. If you don't know the answer, just say that you don't know. Use three sentences maximum and keep the answer concise.
            Question: {%s}
            Context: {%s}
            Answer:
            """;

    // Pre-pull this image to avoid testcontainers image pull timeouts:
    // docker pull gvenzl/oracle-free:23.4-slim-faststart
    @Container
    @ServiceConnection
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.6-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    public void newsWorkflow() {

    }

    private DataSource testContainersDataSource() throws SQLException {
        // Configure a datasource for the Oracle container.
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("NEWS_EVENT_STREAMING");
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        return dataSource;
    }
}
