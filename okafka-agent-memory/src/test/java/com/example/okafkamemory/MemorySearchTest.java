package com.example.okafkamemory;

import com.example.okafkamemory.retrieval.MemorySearchRepository;
import com.example.okafkamemory.retrieval.MemorySearchRequest;
import com.example.okafkamemory.retrieval.MemorySearchResult;
import com.example.okafkamemory.retrieval.MemorySearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.document.Document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemorySearchTest {
    private final TestRepository repository = new TestRepository();
    private final TestEmbeddings embeddings = new TestEmbeddings();
    private final MemorySearchService search = new MemorySearchService(repository, embeddings,
            "user:demo", 0.5, 0.35, 0.15, 30, 3, 0.5);

    @Test
    void invalidQueriesNeverCallOciOrTheRepository() {
        String tooManyTerms = IntStream.range(0, 33).mapToObj(i -> "term" + i).collect(Collectors.joining(" "));
        for (String query : List.of("x".repeat(1025), tooManyTerms, "!!!", " ")) {
            assertThatThrownBy(() -> search.search(new MemorySearchRequest(query, 5)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(embeddings.calls).isZero();
        assertThat(repository.calls).isZero();
    }

    @Test
    void queriesAtTheLengthAndTermLimitsRemainValid() {
        search.search(new MemorySearchRequest("x".repeat(1024), 5));
        String terms = IntStream.range(0, 32).mapToObj(i -> "term" + i).collect(Collectors.joining(" "));
        search.search(new MemorySearchRequest(terms, 5));
        assertThat(embeddings.calls).isEqualTo(2);
        assertThat(repository.calls).isEqualTo(2);
    }

    @Test
    void relevanceCutoffIgnoresRecencyAndAllowsStrongSemanticMatches() {
        var recentIrrelevant = new MemorySearchRepository.Hit(UUID.randomUUID(), "unrelated", OffsetDateTime.now(), 0.7, 0.2);
        var oldSemanticMatch = new MemorySearchRepository.Hit(UUID.randomUUID(), "paraphrase", OffsetDateTime.now().minusYears(2), 0.86, 0);
        var boundary = new MemorySearchRepository.Hit(UUID.randomUUID(), "boundary", OffsetDateTime.now(), 0.85, 0);
        repository.hits = List.of(recentIrrelevant, oldSemanticMatch, boundary);
        assertThat(search.search(new MemorySearchRequest("query", 5)))
                .extracting(MemorySearchResult::memoryId).containsExactly(boundary.memoryId(), oldSemanticMatch.memoryId());
    }

    private static class TestRepository extends MemorySearchRepository {
        int calls;
        List<Hit> hits = List.of();

        TestRepository() {
            super(null);
        }

        @Override
        public List<Hit> find(String owner, float[] vector, Set<String> terms) {
            calls++;
            return hits;
        }
    }

    private static class TestEmbeddings implements EmbeddingModel {
        int calls;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            calls++;
            return new EmbeddingResponse(List.of(new Embedding(new float[]{1, 0, 0}, 0)));
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }
    }
}
