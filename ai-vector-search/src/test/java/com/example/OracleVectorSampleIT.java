package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.List;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import javax.sql.DataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class OracleVectorSampleIT {
    // The local AllMiniLmL6V2QuantizedEmbeddingModel we're using for this test embeds text as 384-dimensional vectors.
    private static final int DIMENSIONS = 384;
    private static final String TABLE = "vector_store";

    // Pre-pull this image to avoid testcontainers image pull timeouts:
    // docker pull gvenzl/oracle-free:23.4-slim-faststart
    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.8-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpwd");

    @Test
    void vectorDatabaseExample() throws SQLException {
        // Configure a datasource for the Oracle container.
        DataSource dataSource = getDataSource();

        // Create a OracleVectorSample instance.
        OracleVectorSample vectorStore = new OracleVectorSample(dataSource, TABLE, DIMENSIONS);
        // Create the vector table in the database.
        vectorStore.createTableIfNotExists();
        System.out.println("Initialized Vector Store");

        // Create a local embedding model for creating embeddings.
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // Generate a list of text embeddings from "country_facts.txt", and use them to populate the database.
        List<Embedding> embeddings = getEmbeddings(embeddingModel);
        vectorStore.addAll(embeddings);
        System.out.println("Populated vector store embeddings");

        // Search the vector store using similarity search
        String searchText = "german castles";
        List<Embedding> results = vectorStore.search(new SearchRequest(
                searchText,
                embeddingModel.embed(searchText).content().vector(),
                1,
                0.0
        ));
        assertThat(results).hasSize(1);
        // The similarity search should return any embeddings from our country_facts.txt related to "german castles"
        assertThat(results.getFirst().content()).isEqualTo("Germany has over 20,000 castles.");
        System.out.println("Found expected content with similarity search");
    }

    private List<Embedding> getEmbeddings(EmbeddingModel embeddingModel) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("country_facts.txt")))) {
            return reader.lines().map(l -> new Embedding(embeddingModel.embed(l).content().vector(), l))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    DataSource getDataSource() throws SQLException {
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("VECTOR_SAMPLE");
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        return dataSource;
    }
}
