package com.example;

import java.io.IOException;
import java.nio.file.Paths;

import com.example.chat.OCIChatService;
import com.example.embeddingmodel.OCIEmbeddingService;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import static com.example.chat.OCIChatService.InferenceRequestType.COHERE;

@SpringBootApplication
public class VectorStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(VectorStoreApplication.class, args);
    }

    @Value("${OCI_COMPARTMENT}")
    private String compartmentId;

    @Value("${OCI_CHAT_MODEL_ID}")
    private String chatModelId;

    @Value("${OCI_EMBEDDING_MODEL_ID}")
    private String embeddingModelId;

    @Bean
    public BasicAuthenticationDetailsProvider authProvider() throws IOException {
        // Create an OCI authentication provider using the default local
        // config file.
        return new ConfigFileAuthenticationDetailsProvider(
                Paths.get(System.getProperty("user.home"), ".oci", "config")
                        .toString(),
                "DEFAULT"
        );
    }

    @Bean
    public OCIChatService chatService(BasicAuthenticationDetailsProvider authProvider) {
        // Create a chat service for an On-Demand OCI GenAI chat model.
        OnDemandServingMode chatServingMode = OnDemandServingMode.builder()
                .modelId(chatModelId)
                .build();
        return OCIChatService.builder()
                .authProvider(authProvider)
                .servingMode(chatServingMode)
                .inferenceRequestType(COHERE)
                .compartment(compartmentId)
                .build();
    }

    @Bean
    public OCIEmbeddingService embeddingModel(BasicAuthenticationDetailsProvider authProvider) {
        OnDemandServingMode embeddingServingMode = OnDemandServingMode.builder()
                .modelId(embeddingModelId)
                .build();
        // Create an OCI Embedding model for text embedding.
        return OCIEmbeddingService.builder()
                .servingMode(embeddingServingMode)
                .aiClient(GenerativeAiInferenceClient.builder()
                        .build(authProvider))
                .compartmentId(compartmentId)
                .build();
    }
}
