package com.example.okafkamemory;

import com.example.okafkamemory.retrieval.MemorySearchTerms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A small live calibration set, not a general retrieval-quality benchmark. */
@SpringBootTest(properties = {
        "memory.background-processing.enabled=false",
        "spring.datasource.oracleucp.initial-pool-size=0",
        "spring.datasource.oracleucp.min-pool-size=0"
})
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT_ID", matches = ".+")
class OciSearchRelevanceTest {
    @Autowired EmbeddingModel embeddings;
    @Value("${memory.retrieval.minimum-relevance}") double minimumRelevance;
    @Value("${memory.retrieval.vector-weight}") double vectorWeight;
    @Value("${memory.retrieval.text-weight}") double textWeight;

    @Test
    void relevantQueriesPassAndUnrelatedQueriesAbstain() throws Exception {
        Examples examples;
        try (var input = getClass().getResourceAsStream("/memory-search-examples.json")) {
            examples = new ObjectMapper().readValue(input, Examples.class);
        }
        var vectors = embeddings.embed(examples.memories());
        double smallestRelevant = 1;
        double largestUnrelated = 0;
        for (Query example : examples.queries()) {
            float[] queryVector = embeddings.embed(example.query());
            var terms = MemorySearchTerms.queryTerms(example.query());
            for (int i = 0; i < examples.memories().size(); i++) {
                double vectorScore = (1 + cosine(queryVector, vectors.get(i))) / 2;
                double textScore = MemorySearchTerms.score(examples.memories().get(i), terms);
                double relevance = (vectorWeight * vectorScore + textWeight * textScore) / (vectorWeight + textWeight);
                System.out.printf("query=%s memory=%d relevance=%.4f%n", example.query(), i, relevance);
                if (example.relevantMemory() == null) {
                    largestUnrelated = Math.max(largestUnrelated, relevance);
                } else if (example.relevantMemory() == i) {
                    smallestRelevant = Math.min(smallestRelevant, relevance);
                }
            }
        }
        System.out.printf("Calibration: max unrelated=%.4f, min relevant=%.4f, threshold=%.4f%n",
                largestUnrelated, smallestRelevant, minimumRelevance);
        assertThat(largestUnrelated).as("unrelated queries must abstain").isLessThan(minimumRelevance);
        assertThat(smallestRelevant).as("relevant queries must qualify").isGreaterThanOrEqualTo(minimumRelevance);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        return dot / Math.sqrt(aa * bb);
    }

    private record Examples(List<String> memories, List<Query> queries) {
    }

    private record Query(String query, Integer relevantMemory) {
    }
}
