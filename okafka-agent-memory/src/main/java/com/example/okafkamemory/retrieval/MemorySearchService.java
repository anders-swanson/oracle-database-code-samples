package com.example.okafkamemory.retrieval;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class MemorySearchService {
    private final MemorySearchRepository repository;
    private final EmbeddingModel embeddings;
    private final String ownerScope;
    private final double vectorWeight;
    private final double textWeight;
    private final double recencyWeight;
    private final double halfLifeDays;
    private final int dimensions;
    private final double minimumRelevance;

    public MemorySearchService(MemorySearchRepository repository, EmbeddingModel embeddings,
                               @Value("${memory.retrieval.owner-scope}") String ownerScope,
                               @Value("${memory.retrieval.vector-weight}") double vectorWeight,
                               @Value("${memory.retrieval.text-weight}") double textWeight,
                               @Value("${memory.retrieval.recency-weight}") double recencyWeight,
                               @Value("${memory.retrieval.recency-half-life-days}") double halfLifeDays,
                               @Value("${spring.ai.oci.genai.embedding.dimensions}") int dimensions,
                               @Value("${memory.retrieval.minimum-relevance}") double minimumRelevance) {
        if (ownerScope.isBlank()
                || vectorWeight < 0 || textWeight < 0 || recencyWeight < 0
                || Math.abs(vectorWeight + textWeight + recencyWeight - 1.0) > 0.000001
                || vectorWeight + textWeight <= 0 || halfLifeDays <= 0 || dimensions < 1
                || !Double.isFinite(minimumRelevance) || minimumRelevance < 0 || minimumRelevance > 1) {
            throw new IllegalArgumentException("Invalid memory retrieval configuration");
        }
        this.repository = repository;
        this.embeddings = embeddings;
        this.ownerScope = ownerScope;
        this.vectorWeight = vectorWeight;
        this.textWeight = textWeight;
        this.recencyWeight = recencyWeight;
        this.halfLifeDays = halfLifeDays;
        this.dimensions = dimensions;
        this.minimumRelevance = minimumRelevance;
    }

    public List<MemorySearchResult> search(MemorySearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()
                || request.limit() < 1 || request.limit() > 50) {
            throw new IllegalArgumentException("query is required and limit must be between 1 and 50");
        }
        var terms = MemorySearchTerms.queryTerms(request.query());
        String query = request.query().trim();
        float[] vector = embeddings.embed(query);
        if (vector == null || vector.length != dimensions) {
            throw new IllegalStateException("Query embedding dimension must be " + dimensions);
        }
        OffsetDateTime now = OffsetDateTime.now();
        return repository.find(ownerScope, vector, terms).stream()
                // Recency ranks relevant memories; it cannot make an irrelevant memory qualify.
                .filter(hit -> (vectorWeight * Math.clamp(hit.vectorScore(), 0, 1)
                        + textWeight * hit.textScore()) / (vectorWeight + textWeight) >= minimumRelevance)
                .map(hit -> score(hit, now))
                .sorted(Comparator.comparingDouble(MemorySearchResult::score).reversed()
                        .thenComparing(MemorySearchResult::memoryId))
                .limit(request.limit()).toList();
    }

    private MemorySearchResult score(MemorySearchRepository.Hit hit, OffsetDateTime now) {
        double ageDays = Math.max(0, Duration.between(hit.createdAt(), now).toSeconds() / 86400.0);
        double recency = Math.pow(0.5, ageDays / halfLifeDays);
        double vector = Math.clamp(hit.vectorScore(), 0, 1);
        double text = Math.clamp(hit.textScore(), 0, 1);
        double score = vectorWeight * vector + textWeight * text + recencyWeight * recency;
        return new MemorySearchResult(hit.memoryId(), hit.memoryText(),
                Map.of("status", "active"),
                score, vector, text, recency);
    }
}
