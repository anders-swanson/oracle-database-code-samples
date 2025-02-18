package com.example.workflow;

import com.example.documentloader.OCIDocumentLoader;
import com.example.embeddingmodel.OCIEmbeddingService;
import com.example.splitter.Splitter;
import com.example.vectorstore.OracleVectorStore;
import lombok.Builder;

@Builder
public class EmbeddingWorkflow implements Runnable {
    private final OracleVectorStore vectorStore;
    private final OCIEmbeddingService embeddingModel;
    private final OCIDocumentLoader documentLoader;
    /**
     * Object Storage namespace.
     */
    private final String namespace;
    /**
     * Object Storage bucket name.
     */
    private final String bucketName;
    /**
     * Object Storage prefix or object name to load documents from.
     */
    private final String objectPrefix;
    /**
     * The Splitter used to break documents into chunks.
     */
    private final Splitter<String> splitter;

    @Override
    public void run() {
        // Stream documents from OCI object storage.
        documentLoader.streamDocuments(bucketName, objectPrefix)
                // Split each object storage document into chunks.
                .map(splitter::split)
                // Embed each chunk list using OCI GenAI service.
                .map(embeddingModel::embedAll)
                // Store embeddings in Oracle Database 23ai.
                .forEach(vectorStore::addAll);
    }
}
