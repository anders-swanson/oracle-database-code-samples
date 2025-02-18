package com.example;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.sql.SQLException;

import com.example.chat.OCIChatService;
import com.example.documentloader.OCIDocumentLoader;
import com.example.embeddingmodel.OCIEmbeddingService;
import com.example.splitter.LineSplitter;
import com.example.vectorstore.OracleVectorStore;
import com.example.workflow.ChatWorkflow;
import com.example.workflow.EmbeddingWorkflow;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static com.example.chat.OCIChatService.InferenceRequestType.COHERE;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_CHAT_MODEL_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_EMBEDDING_MODEL_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_NAMESPACE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_BUCKET_NAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OCI_OBJECT_PREFIX", matches = ".+")
public class OCIApplicationIT {
    private final String compartmentId = System.getenv("OCI_COMPARTMENT");
    // You can find your model id in the OCI Console.
    private final String chatModelId = System.getenv("OCI_CHAT_MODEL_ID");
    private final String embeddingModelId = System.getenv("OCI_EMBEDDING_MODEL_ID");
    private final String namespace = System.getenv("OCI_NAMESPACE");
    private final String bucketName = System.getenv("OCI_BUCKET_NAME");
    private final String objectPrefix = System.getenv("OCI_OBJECT_PREFIX");

    // Use a vector dimension size specific to the embeddings you generate.
    // OCI Embedding Service uses 1024 dimensional vectors.
    private final int vectorDimensions = 1024;
    private final String tableName = "vector_store";

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
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart")
            .withUsername("testuser")
            .withPassword(("testpwd"));


    @Test
    public void chatExample() throws Exception {
        // Create an OracleVectorStore to hold our embeddings.
        DataSource ds = testContainersDataSource();
        OracleVectorStore vectorStore = new OracleVectorStore(
                ds,
                tableName,
                vectorDimensions
        );
        vectorStore.createTableIfNotExists();

        // Create an OCI authentication provider using the default local
        // config file.
        var authProvider = new ConfigFileAuthenticationDetailsProvider(
                Paths.get(System.getProperty("user.home"), ".oci", "config")
                        .toString(),
                "DEFAULT"
        );
        // Create an object storage document loader to load texts
        OCIDocumentLoader documentLoader = new OCIDocumentLoader(
                ObjectStorageClient.builder().build(authProvider),
                namespace
        );
        OnDemandServingMode embeddingServingMode = OnDemandServingMode.builder()
                .modelId(embeddingModelId)
                .build();
        // Create an OCI Embedding model for text embedding.
        OCIEmbeddingService embeddingModel = OCIEmbeddingService.builder()
                .servingMode(embeddingServingMode)
                .aiClient(GenerativeAiInferenceClient.builder()
                        .build(authProvider))
                .compartmentId(compartmentId)
                .build();

        // Create a chat service for an On-Demand OCI GenAI chat model.
        OnDemandServingMode chatServingMode = OnDemandServingMode.builder()
                .modelId(chatModelId)
                .build();
        OCIChatService chatService = OCIChatService.builder()
                .authProvider(authProvider)
                .servingMode(chatServingMode)
                .inferenceRequestType(COHERE)
                .compartment(compartmentId)
                .build();

        // Create a workflow that will load, split, embed, and store documents.
        EmbeddingWorkflow embeddingWorkflow = EmbeddingWorkflow.builder()
                .vectorStore(vectorStore)
                .embeddingModel(embeddingModel)
                .documentLoader(documentLoader)
                .splitter(new LineSplitter())
                .namespace(namespace)
                .bucketName(bucketName)
                .objectPrefix(objectPrefix)
                .build();
        // Run the embedding workflow to store all our document embeddings
        // in the database.
        embeddingWorkflow.run();

        String userQuestion = "What is Germany famous for?";
        // Create a workflow to embed the user question, query the vector
        // database for related content, and then call the chat service
        // with the content-enriched prompt.
        ChatWorkflow chatWorkflow = ChatWorkflow.builder()
                .vectorStore(vectorStore)
                .chatService(chatService)
                .embeddingModel(embeddingModel)
                .minScore(0.7)
                .promptTemplate(promptTemplate)
                .userQuestion(userQuestion)
                .build();
        // Call the chat workflow
        String response = chatWorkflow.call();
        // The document loaded contains information about Germany's Oktoberfest
        // tradition.
        assertThat(response).containsIgnoringCase("oktoberfest");
        System.out.println(response);
    }

    private DataSource testContainersDataSource() throws SQLException {
        // Configure a datasource for the Oracle container.
        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("VECTOR_SAMPLE");
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        return dataSource;
    }
}
