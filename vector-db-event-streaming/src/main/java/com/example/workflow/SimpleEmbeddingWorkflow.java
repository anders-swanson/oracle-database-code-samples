package com.example.workflow;

import java.util.List;

import com.example.embeddingmodel.OCIEmbeddingService;
import com.example.splitter.Splitter;
import com.example.vectorstore.OracleVectorStore;
import lombok.Builder;

@Builder
public class SimpleEmbeddingWorkflow implements Runnable {
    private final OracleVectorStore vectorStore;
    private final OCIEmbeddingService embeddingModel;

    /**
     * List of documents to embed.
     */
    private final List<String> documents;

    /**
     * The Splitter used to break documents into chunks.
     */
    private final Splitter<String> splitter;

    @Override
    public void run() {
        documents.stream()
                // Split each object storage document into chunks.
                .map(splitter::split)
                // Embed each chunk list using OCI GenAI service.
                .map(embeddingModel::embedAll)
                // Store embeddings in Oracle Database 23ai.
                .forEach(vectorStore::addAll);
    }
}
