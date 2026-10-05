package com.example.okafkamemory.embedding;

import com.example.okafkamemory.memory.JdbcMemoryRepository;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.UUID;

@Service
public class MemoryEmbeddingService {
    private final SingleConnectionJdbcClientFactory clients;
    private final EmbeddingModel model;
    private final int dimensions;

    public MemoryEmbeddingService(SingleConnectionJdbcClientFactory clients, EmbeddingModel model,
                                  @Value("${spring.ai.oci.genai.embedding.dimensions}") int dimensions) {
        this.clients = clients;
        this.model = model;
        this.dimensions = dimensions;
    }

    public void embed(Connection connection, UUID candidateId) {
        var memories = new JdbcMemoryRepository(clients.create(connection));
        memories.pendingText(candidateId).ifPresent(text -> {
            float[] vector = model.embed(text);
            if (vector == null || vector.length != dimensions) {
                throw new IllegalStateException("Embedding dimension must be " + dimensions);
            }
            memories.completeEmbedding(candidateId, vector);
        });
    }
}
