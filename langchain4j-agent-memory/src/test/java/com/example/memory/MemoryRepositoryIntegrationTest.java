package com.example.memory;

import com.example.memory.agent.MemoryTools;
import com.example.memory.model.MemoryHit;
import com.example.memory.search.MemoryRepository;
import com.example.memory.search.MemorySearchRequest;
import oracle.jdbc.datasource.impl.OracleDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MemoryRepositoryIntegrationTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.1-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    private MemoryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser(oracle.getUsername());
        dataSource.setPassword(oracle.getPassword());
        repository = new MemoryRepository(dataSource, new EmbeddingClient(System.getenv("OPENAI_API_KEY")));
        repository.initializeSchema();
        repository.seedIfEmpty(SeedMemories.records());
    }

    @Test
    void textCombinedSearchPrefersExactIncidentAndChangeTicket() {
        List<MemoryHit> hits = repository.textSearch("CHG2145 ACCUM INC4721 ACCUM checkout", 5);

        MemoryHit hit = findByTitle(hits, "Checkout incident after CHG2145");
        assertEquals("INC4721", hit.incidentId());
    }

    @Test
    void vectorCombinedSearchFindsParaphrasedCheckoutFailure() {
        List<MemoryHit> hits = repository.vectorSearch("customers could browse but orders failed right after the checkout rollout incident", 5);

        MemoryHit hit = findByTitle(hits, "Checkout incident after CHG2145");
        assertTrue(hit.vectorScore() > 0.0d);
    }

    @Test
    void hybridFusionPromotesBothChannelMatch() {
        List<MemoryHit> hits = repository.combinedSearch(new MemorySearchRequest("What happened during INC4721 after CHG2145 on checkout?", 5));

        MemoryHit hit = findByTitle(hits, "Checkout incident after CHG2145");
        assertEquals("BOTH", hit.matchedBy());
    }

    @Test
    void toolWritebackCanBeFoundOnNextCombinedSearch() {
        MemoryTools tools = new MemoryTools(repository);
        String storedTitle = "Stored next shift note for checkout follow-up";

        tools.storeMemory(
                "HANDOFF",
                storedTitle,
                "Watch duplicate captures for one more hour.",
                "The next shift should monitor duplicate payment captures and confirm the checkout retry queue stays below 50.",
                "checkout",
                "prod",
                "INC4721",
                "CHG2145",
                "handoff,checkout,payments"
        );

        List<MemoryHit> hits = repository.combinedSearch(new MemorySearchRequest(
                "Which handoff says the checkout retry queue stays below 50 while watching duplicate captures after CHG2145?",
                5
        ));
        assertEquals(storedTitle, findByTitle(hits, storedTitle).title());
    }

    private MemoryHit findByTitle(List<MemoryHit> hits, String title) {
        return hits.stream()
                .filter(hit -> title.equals(hit.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected to find title in top sample: " + title));
    }
}
