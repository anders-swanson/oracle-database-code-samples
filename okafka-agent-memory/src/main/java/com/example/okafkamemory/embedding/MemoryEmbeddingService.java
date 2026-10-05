package com.example.okafkamemory.embedding;

import com.example.okafkamemory.memory.JdbcMemoryRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.UUID;

@Service
public class MemoryEmbeddingService {
    private final EmbeddingModel model;
    private final int dimensions;

    public MemoryEmbeddingService(EmbeddingModel model,
                                  @Value("${spring.ai.oci.genai.embedding.dimensions}") int dimensions) {
        this.model = model;
        this.dimensions = dimensions;
    }

    public void embed(Connection connection, UUID candidateId) {
        var memories = JdbcMemoryRepository.from(connection);
        memories.findText(candidateId).ifPresent(text -> {
            float[] vector = model.embed(text);
            if (vector == null || vector.length != dimensions) {
                throw new IllegalStateException("Embedding dimension must be " + dimensions);
            }
            memories.updateEmbedding(candidateId, vector);
        });
    }
}
