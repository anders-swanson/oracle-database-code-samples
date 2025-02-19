package com.example.news.genai;

import java.io.IOException;
import java.nio.file.Paths;

import com.example.news.genai.chat.ChatService;
import com.example.news.genai.chat.OCIChatService;
import com.example.news.genai.embeddingmodel.EmbeddingService;
import com.example.news.genai.embeddingmodel.OCIEmbeddingService;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.example.news.genai.chat.OCIChatService.InferenceRequestType.COHERE;

@Configuration
public class GenAIConfiguration {
    @Value("${oci.compartment}")
    private String compartmentId;

    @Value("${oci.chatModelID}")
    private String chatModelId;

    @Value("${oci.embeddingModelID}")
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
    public ChatService chatService(BasicAuthenticationDetailsProvider authProvider) {
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
    public EmbeddingService embeddingModel(BasicAuthenticationDetailsProvider authProvider) {
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
