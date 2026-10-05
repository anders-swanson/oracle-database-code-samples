package com.example.okafkamemory;

import com.example.okafkamemory.candidate.CandidatePreparationService;
import com.example.okafkamemory.candidate.CandidateWork;
import com.example.okafkamemory.candidate.CandidateWorkRepository;
import com.example.okafkamemory.curation.CurationService;
import com.example.okafkamemory.embedding.MemoryEmbeddingService;
import com.example.okafkamemory.intake.IncomingEvent;
import com.example.okafkamemory.intake.IntakeResult;
import com.example.okafkamemory.intake.IntakeService;
import com.example.okafkamemory.retrieval.MemorySearchRequest;
import com.example.okafkamemory.retrieval.MemorySearchService;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.TranscriptRepository;
import com.oracle.spring.testcontainers.OracleContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "memory.background-processing.enabled=false",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.oci.genai.chat.compartment-id=test-compartment",
        "spring.ai.oci.genai.embedding.compartment-id=test-compartment",
        "spring.datasource.oracleucp.connection-pool-name=okafka-agent-memory-flow-test"
})
@Import(MemoryFlowTest.Models.class)
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = "/db/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class MemoryFlowTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Models {
        @Bean
        ChatModel chatModel() {
            return new FixtureChatModel();
        }

        @Bean
        EmbeddingModel embeddingModel(@Value("${spring.ai.oci.genai.embedding.dimensions}") int dimensions) {
            return new FixtureEmbeddingModel(dimensions);
        }
    }

    @Container @ServiceConnection
    private static final OracleContainer oracle = new OracleContainer()
            .withUsername("TESTUSER").withPassword("Welcome123#");

    @Autowired DataSource dataSource;
    @Autowired IntakeService intake;
    private TranscriptRepository transcripts;
    @Autowired CandidatePreparationService preparation;
    private CandidateWorkRepository candidates;
    @Autowired CurationService curation;
    @Autowired MemoryEmbeddingService embeddings;
    @Autowired MemorySearchService search;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void repositories() {
        transcripts = new JdbcTranscriptRepository(jdbc);
        candidates = new CandidateWorkRepository(jdbc);
    }

    @Test
    void intakeAndPreparationKeepOnlyAllowedFacts() {
        UUID filteredId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();
        UUID zeroId = UUID.randomUUID();
        UUID multiId = UUID.randomUUID();
        var filtered = process(new IncomingEvent(filteredId, "user:demo",
                "{\"secret\":\"must not be stored\"}", false));
        assertThat(filtered.outcome()).isEqualTo(IntakeResult.Outcome.FILTERED);
        assertThat(filtered.transcriptId()).isNull();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(filteredId))
                .query(Integer.class).single()).isZero();

        var first = process(new IncomingEvent(duplicateId, "user:demo",
                "{\"message\":\"remember first fact\"}", true));
        var duplicate = process(new IncomingEvent(duplicateId, "user:other",
                "{\"message\":\"remember different fact\"}", true));
        assertThat(duplicate).isEqualTo(first);
        assertThat(transcripts.findBySourceEventId(duplicateId).orElseThrow().sourceEventId())
                .isEqualTo(duplicateId);
        for (String table : List.of("intake_outcomes", "transcripts")) {
            assertThat(jdbc.sql("SELECT VSIZE(source_event_id) FROM " + table + " WHERE source_event_id = ?")
                    .param(UuidBytes.encode(duplicateId)).query(Integer.class).single()).isEqualTo(16);
        }
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO intake_outcomes (source_event_id, outcome) VALUES (?, 'FILTERED')")
                .param(new byte[15]).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(transcripts.findById(first.transcriptId()).orElseThrow().eventPayload())
                .contains("first fact");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(duplicateId))
                .query(Integer.class).single()).isEqualTo(1);

        process(new IncomingEvent(zeroId, "user:demo", "{\"message\":\"hello\"}", true));
        assertThat(prepare(zeroId)).isEmpty();
        assertThat(jdbc.sql("SELECT preparation_status FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(zeroId))
                .query(String.class).single()).isEqualTo("NO_MEMORY");

        process(new IncomingEvent(multiId, "user:demo", """
                {"messages":[{"role":"user","text":"remember first fact"},
                             {"role":"user","text":"remember second fact"},
                             {"role":"user","text":"remember my password is hunter2"}]}
                """, true));
        List<CandidateWork> found = prepare(multiId);
        assertThat(found).hasSize(2);
        assertThat(found).extracting(CandidateWork::candidateText)
                .containsExactlyInAnyOrder("first fact", "second fact");
        assertThat(candidates.findCandidates(multiId)).hasSize(2);
    }

    @Test
    void decisionsAndSearchUseFourTablePath() throws Exception {
        CandidateWork promoted = candidate("user:demo", "remember I prefer dark mode");
        CandidateWork review = candidate("user:demo", "remember maybe I like tea");
        CandidateWork rejected = candidate("user:demo", "remember reject this note");
        curate(promoted.candidateId());
        curate(review.candidateId());
        curate(rejected.candidateId());
        assertThat(candidateStatus(review)).isEqualTo("REVIEW");
        assertThat(candidateStatus(rejected)).isEqualTo("REJECTED");
        assertThat(candidateStatus(promoted)).isEqualTo("PENDING_EMBEDDING");
        assertThat(search.search(new MemorySearchRequest("dark mode", 5)))
                .noneMatch(hit -> hit.memoryText().equals("I prefer dark mode"));
        assertThat(jdbc.sql("""
                SELECT VSIZE(m.id) FROM event_memories m WHERE m.candidate_id = ?
                """).param(UuidBytes.encode(promoted.candidateId()))
                .query(Integer.class).single()).isEqualTo(16);

        embed(promoted.candidateId());
        assertThat(candidateStatus(promoted)).isEqualTo("COMPLETE");
        assertThat(search.search(new MemorySearchRequest("dark mode", 5)))
                .anySatisfy(hit -> {
                    assertThat(hit.memoryText()).isEqualTo("I prefer dark mode");
                    assertThat(hit.vectorScore()).isPositive();
                    assertThat(hit.textScore()).isPositive();
                });
        assertThat(search.search(new MemorySearchRequest("night theme", 5)))
                .anySatisfy(hit -> {
                    assertThat(hit.memoryText()).isEqualTo("I prefer dark mode");
                    assertThat(hit.textScore()).isZero();
                });
        mvc.perform(post("/api/memories/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"dark mode\",\"limit\":5,\"ownerScope\":\"user:other\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memoryText").value("I prefer dark mode"))
                .andExpect(jsonPath("$[0].eventPayload").doesNotExist());
    }

    @Test
    void failedEmbeddingRollsBackAndCanBeRetried() {
        CandidateWork failed = candidate("user:demo", "remember retryable memory");
        CandidateWork other = candidate("user:demo", "remember independent memory");
        curate(failed.candidateId());
        curate(other.candidateId());
        String hex = failed.candidateId().toString().replace("-", "");
        jdbc.sql("""
                CREATE OR REPLACE TRIGGER fail_one_embedding
                BEFORE UPDATE OF embedding ON event_memories FOR EACH ROW
                BEGIN
                    IF :NEW.candidate_id = HEXTORAW('%s') THEN
                        RAISE_APPLICATION_ERROR(-20003, 'simulated embedding failure');
                    END IF;
                END;
                """.formatted(hex)).update();
        try {
            assertThatThrownBy(() -> embed(failed.candidateId())).isInstanceOf(RuntimeException.class);
            assertThat(candidateStatus(failed)).isEqualTo("PENDING_EMBEDDING");
            embed(other.candidateId());
            assertThat(candidateStatus(other)).isEqualTo("COMPLETE");
        } finally {
            jdbc.sql("DROP TRIGGER fail_one_embedding").update();
        }
        embed(failed.candidateId());
        assertThat(candidateStatus(failed)).isEqualTo("COMPLETE");
    }

    @Test
    void searchExcludesOtherOwnersExpiredAndInactive() {
        CandidateWork foreign = completed("user:other", "dark mode foreign");
        CandidateWork expired = completed("user:demo", "dark mode expired");
        CandidateWork inactive = completed("user:demo", "dark mode inactive");
        jdbc.sql("UPDATE event_memories SET expires_at = SYSTIMESTAMP - INTERVAL '1' DAY WHERE candidate_id = ?")
                .param(UuidBytes.encode(expired.candidateId())).update();
        jdbc.sql("UPDATE event_memories SET status = 'inactive' WHERE candidate_id = ?")
                .param(UuidBytes.encode(inactive.candidateId())).update();
        var hits = search.search(new MemorySearchRequest("dark mode", 50));
        assertThat(hits).noneMatch(hit -> hit.memoryText().contains("foreign")
                || hit.memoryText().contains("expired") || hit.memoryText().contains("inactive"));
        assertThat(candidateStatus(foreign)).isEqualTo("COMPLETE");
    }

    private CandidateWork completed(String owner, String text) {
        CandidateWork candidate = candidate(owner, "remember " + text);
        curate(candidate.candidateId());
        embed(candidate.candidateId());
        return candidate;
    }

    private CandidateWork candidate(String owner, String message) {
        UUID source = UUID.randomUUID();
        process(new IncomingEvent(source, owner, "{\"message\":\"" + message + "\"}", true));
        return prepare(source).getFirst();
    }

    private String candidateStatus(CandidateWork candidate) {
        return jdbc.sql("SELECT status FROM candidate_work WHERE candidate_id = ?")
                .param(UuidBytes.encode(candidate.candidateId())).query(String.class).single();
    }

    private IntakeResult process(IncomingEvent event) {
        return transaction(connection -> intake.process(connection, event, (topic, key, value) -> {}, "transcripts"));
    }

    private List<CandidateWork> prepare(UUID sourceEventId) {
        long id = transcripts.findBySourceEventId(sourceEventId).orElseThrow().transcriptId();
        transaction(connection -> {
            preparation.process(connection, id, (topic, key, value) -> {}, "candidates");
            return null;
        });
        return candidates.findCandidates(sourceEventId);
    }

    private void curate(UUID id) {
        transaction(connection -> {
            curation.curate(connection, id, (topic, key, value) -> {}, "embeddings");
            return null;
        });
    }

    private void embed(UUID id) {
        transaction(connection -> {
            embeddings.embed(connection, id);
            return null;
        });
    }

    @FunctionalInterface
    private interface Work<T> {
        T execute(java.sql.Connection connection) throws Exception;
    }

    private <T> T transaction(Work<T> work) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalStateException("Test transaction failed", error);
        }
    }
}
