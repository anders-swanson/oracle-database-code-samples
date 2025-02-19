package com.example.news.genai.workflow;

import java.sql.Connection;
import java.util.List;

import com.example.news.genai.embeddingmodel.EmbeddingService;
import com.example.news.genai.vectorstore.Embedding;
import com.example.news.genai.vectorstore.NewsVectorStore;
import lombok.Builder;

@Builder
public class EmbeddingWorkflow implements Runnable {
    private final NewsVectorStore vectorStore;
    private final EmbeddingService embeddingModel;
    private final Connection connection;

    /**
     * List of documents to embed.
     */
    private final List<String> documents;

    @Override
    public void run() {
        List<Embedding> embeddings = embeddingModel.embedAll(documents);
        vectorStore.addAll(embeddings, connection);
    }
}
