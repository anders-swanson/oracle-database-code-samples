package com.example.okafkamemory.embedding;

import com.example.okafkamemory.memory.JdbcMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.UUID;

@Service
public class MemoryEmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(MemoryEmbeddingService.class);
    private final EmbeddingModel model;
    private final int dimensions;

    public MemoryEmbeddingService(EmbeddingModel model,
                                  @Value("${spring.ai.oci.genai.embedding.dimensions}") int dimensions) {
        this.model = model;
        this.dimensions = dimensions;
    }

    public void embed(Connection connection, UUID memoryId) {
        var memories = JdbcMemoryRepository.from(connection);
        memories.findText(memoryId).ifPresentOrElse(text -> {
            log.info("At the embedding stage: asking the model to generate an embedding for memory {}.", memoryId);
            float[] vector = model.embed(text);
            if (vector == null || vector.length != dimensions) {
                throw new IllegalStateException("Embedding dimension must be " + dimensions);
            }
            memories.updateEmbedding(memoryId, vector);
            memories.supersedeFor(memoryId);
            log.info("At the embedding stage: saved an embedding with {} dimensions for memory {}; awaiting transaction commit.", vector.length, memoryId);
        }, () -> log.info("At the embedding stage: skipped memory {} because no memory text was found.", memoryId));
    }
}
