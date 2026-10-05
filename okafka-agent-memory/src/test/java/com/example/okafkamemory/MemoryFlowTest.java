package com.example.okafkamemory;

import com.example.okafkamemory.candidate.CandidatePreparationService;
import com.example.okafkamemory.candidate.CandidateWork;
import com.example.okafkamemory.candidate.CandidateWorkRepository;
import com.example.okafkamemory.embedding.MemoryEmbeddingService;
import com.example.okafkamemory.memory.JdbcMemoryRepository;
import oracle.sql.VECTOR;
import com.example.okafkamemory.intake.IncomingEvent;
import com.example.okafkamemory.intake.IntakeService;
import com.example.okafkamemory.retrieval.MemorySearchRequest;
import com.example.okafkamemory.retrieval.MemorySearchService;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.TranscriptRepository;
import com.example.okafkamemory.transcript.Transcript;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import com.oracle.spring.testcontainers.OracleContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "memory.background-processing.enabled=false",
        "memory.candidates.score-threshold=80",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.oci.genai.chat.compartment-id=test-compartment",
        "spring.ai.oci.genai.embedding.compartment-id=test-compartment",
        "spring.datasource.oracleucp.connection-pool-name=okafka-agent-memory-flow-test"
})
@Import(MemoryFlowTest.Models.class)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
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
    void intakeDropsFilteredEventsAndPublishesAcceptedDuplicatesOnlyOnce(CapturedOutput output) {
        var filtered = List.of(
                new IncomingEvent(UUID.randomUUID(), "user:demo", Map.of("secret", "do not log this"), false),
                new IncomingEvent(UUID.randomUUID(), " ", Map.of("message", "hello"), true),
                new IncomingEvent(UUID.randomUUID(), "user:demo", null, true));
        var published = new ArrayList<Object>();
        for (IncomingEvent event : filtered) {
            transaction(connection -> {
                intake.process(connection, event, (topic, key, value) -> published.add(value), "transcripts");
                return null;
            });
            assertThat(transcripts.findBySourceEventId(event.sourceEventId())).isEmpty();
        }
        assertThat(published).isEmpty();
        assertThat(output.getOut()).contains("WARN", "STORAGE_NOT_ALLOWED", "MISSING_OWNER_SCOPE", "EMPTY_TRANSCRIPT")
                .doesNotContain("do not log this");

        // A dropped ID has no durable rejection; a later allowed event can use it.
        UUID source = filtered.getFirst().sourceEventId();
        for (String message : List.of("remember first fact", "remember changed fact")) {
            transaction(connection -> {
                intake.process(connection, new IncomingEvent(source, "user:demo", Map.of("message", message), true),
                        (topic, key, value) -> published.add(value), "transcripts");
                return null;
            });
        }
        assertThat(published).hasSize(1);
        assertThat(transcripts.findBySourceEventId(source).orElseThrow().eventPayload())
                .containsEntry("message", "remember first fact");
    }

    @Test
    void structuredTranscriptSurvivesOsonAndNativeJsonStorage() {
        String evidence = "remember I prefer \"dark\" mode\nwith tabs";
        var payload = Map.<String, Object>of("messages", List.of(
                Map.of("role", "user", "text", evidence)));
        var event = new IncomingEvent(UUID.randomUUID(), "user:demo", payload, true);
        var factory = new OSONKafkaSerializationFactory(JSONB.createDefault());
        try (var serializer = factory.createSerializer();
             var deserializer = factory.createDeserializer(IncomingEvent.class)) {
            var received = deserializer.deserialize("MEMORY_INCOMING",
                    serializer.serialize("MEMORY_INCOMING", event));
            var result = process(received);
            assertThat(transcripts.findById(result.transcriptId()).orElseThrow().eventPayload())
                    .isEqualTo(payload);
            assertThat(jdbc.sql("""
                    SELECT JSON_VALUE(event_payload, '$.messages[0].text')
                    FROM transcripts WHERE transcript_id = ?
                    """).param(result.transcriptId()).query(String.class).single()).isEqualTo(evidence);
            assertThat(jdbc.sql("""
                    SELECT data_type FROM user_tab_columns
                    WHERE table_name = 'TRANSCRIPTS' AND column_name = 'EVENT_PAYLOAD'
                    """).query(String.class).single()).isEqualTo("JSON");
            assertThat(prepare(event.sourceEventId())).singleElement()
                    .satisfies(candidate -> assertThat(candidate.evidence()).isEqualTo(evidence));
        }
    }

    @Test
    void intakeAndPreparationKeepOnlyAllowedFacts() {
        UUID filteredId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();
        UUID zeroId = UUID.randomUUID();
        UUID multiId = UUID.randomUUID();
        var filtered = process(new IncomingEvent(filteredId, "user:demo",
                Map.of("secret", "must not be stored"), false));
        assertThat(filtered).isNull();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(filteredId))
                .query(Integer.class).single()).isZero();

        var first = process(new IncomingEvent(duplicateId, "user:demo",
                Map.of("message", "remember first fact"), true));
        var duplicate = process(new IncomingEvent(duplicateId, "user:other",
                Map.of("message", "remember different fact"), true));
        assertThat(duplicate).isEqualTo(first);
        assertThat(transcripts.findBySourceEventId(duplicateId).orElseThrow().sourceEventId())
                .isEqualTo(duplicateId);
        assertThat(jdbc.sql("SELECT VSIZE(source_event_id) FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(duplicateId)).query(Integer.class).single()).isEqualTo(16);
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO transcripts (source_event_id, owner_scope, event_payload) VALUES (?, 'user:demo', '{}')")
                .param(new byte[15]).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(transcripts.findById(first.transcriptId()).orElseThrow().eventPayload())
                .containsEntry("message", "remember first fact");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(duplicateId))
                .query(Integer.class).single()).isEqualTo(1);

        process(new IncomingEvent(zeroId, "user:demo", Map.of("message", "hello"), true));
        assertThat(prepare(zeroId)).isEmpty();
        assertThat(jdbc.sql("SELECT preparation_status FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(zeroId))
                .query(String.class).single()).isEqualTo("NO_MEMORY");

        process(new IncomingEvent(multiId, "user:demo", Map.of("messages", List.of(
                Map.of("role", "user", "text", "remember first fact"),
                Map.of("role", "user", "text", "remember second fact"),
                Map.of("role", "user", "text", "remember my password is hunter2"))), true));
        List<CandidateWork> found = prepare(multiId);
        assertThat(found).hasSize(2);
        assertThat(found).extracting(CandidateWork::candidateText)
                .containsExactlyInAnyOrder("first fact", "second fact");
        assertThat(candidates.findCandidates(multiId)).hasSize(2);
    }

    @Test
    void onlyCandidatesAboveConfiguredJudgeThresholdArePersisted() {
        UUID source = UUID.randomUUID();
        process(new IncomingEvent(source, "user:demo", Map.of("messages", List.of(
                Map.of("role", "user", "text", "remember threshold fact"),
                Map.of("role", "user", "text", "remember above threshold fact"),
                Map.of("role", "user", "text", "remember maybe I like tea"))), true));
        assertThat(prepare(source)).singleElement().satisfies(candidate -> {
            assertThat(candidate.candidateText()).isEqualTo("above threshold fact");
            assertThat(candidate.judgeScore()).isEqualTo(81);
        });

        UUID rejected = UUID.randomUUID();
        process(new IncomingEvent(rejected, "user:demo", Map.of("message", "remember reject this note"), true));
        assertThat(prepare(rejected)).isEmpty();
        assertThat(jdbc.sql("SELECT preparation_status FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(rejected)).query(String.class).single()).isEqualTo("NO_MEMORY");
    }

    @Test
    void invalidJudgeScoreLeavesTranscriptReadyForRetry() {
        UUID source = UUID.randomUUID();
        process(new IncomingEvent(source, "user:demo", Map.of("message", "remember invalid judge output"), true));
        assertThatThrownBy(() -> prepare(source)).hasRootCauseMessage(
                "Candidate judge must return an integer score between 0 and 100");
        assertThat(candidates.findCandidates(source)).isEmpty();
        assertThat(jdbc.sql("SELECT preparation_status FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(source)).query(String.class).single()).isEqualTo("READY");
    }

    @Test
    void preparationRollsBackCandidateMemoryAndHandoffTogether() {
        UUID source = UUID.randomUUID();
        var transcript = process(new IncomingEvent(source, "user:demo",
                Map.of("message", "remember atomic preparation"), true));
        assertThatThrownBy(() -> transaction(connection -> {
            preparation.process(connection, transcript.transcriptId(), (topic, key, value) -> {
                throw new IllegalStateException("simulated embedding handoff failure");
            }, "embeddings");
            return null;
        })).hasRootCauseMessage("simulated embedding handoff failure");
        assertThat(candidates.findCandidates(source)).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM event_memories m JOIN candidate_work c "
                + "ON c.candidate_id = m.candidate_id WHERE c.transcript_id = ?")
                .param(transcript.transcriptId()).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT preparation_status FROM transcripts WHERE transcript_id = ?")
                .param(transcript.transcriptId()).query(String.class).single()).isEqualTo("READY");

        var published = new ArrayList<Object>();
        for (int attempt = 0; attempt < 2; attempt++) {
            transaction(connection -> {
                preparation.process(connection, transcript.transcriptId(),
                        (topic, key, value) -> published.add(value), "embeddings");
                return null;
            });
        }
        assertThat(candidates.findCandidates(source)).hasSize(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM event_memories m JOIN candidate_work c "
                + "ON c.candidate_id = m.candidate_id WHERE c.transcript_id = ?")
                .param(transcript.transcriptId()).query(Integer.class).single()).isEqualTo(1);
        assertThat(published).singleElement()
                .isInstanceOf(com.example.okafkamemory.events.MemoryReadyForEmbedding.class);
    }

    @Test
    void admittedCandidatesReachSearch() throws Exception {
        CandidateWork promoted = candidate("user:demo", "remember I prefer dark mode");
        assertThat(search.search(new MemorySearchRequest("dark mode", 5)))
                .noneMatch(hit -> hit.memoryText().equals("I prefer dark mode"));
        assertThat(jdbc.sql("""
                SELECT VSIZE(m.id) FROM event_memories m WHERE m.candidate_id = ?
                """).param(UuidBytes.encode(promoted.candidateId()))
                .query(Integer.class).single()).isEqualTo(16);

        embed(promoted.candidateId());
        assertThat(hasEmbedding(promoted)).isTrue();
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
    void embeddingUpdateCanReplaceAnExistingVector() {
        CandidateWork candidate = candidate("user:demo", "remember replaceable embedding");
        embed(candidate.candidateId());
        float[] replacement = {0.25f, 0.5f, 0.75f};
        transaction(connection -> {
            JdbcMemoryRepository.from(connection).updateEmbedding(candidate.candidateId(), replacement);
            return null;
        });
        assertThat(jdbc.sql("SELECT embedding FROM event_memories WHERE candidate_id = ?")
                .param(UuidBytes.encode(candidate.candidateId()))
                .query((rs, row) -> rs.getObject("embedding", VECTOR.class).toFloatArray()).single())
                .containsExactly(replacement);
        // The service can re-embed an existing vector too; it does not rely on candidate status.
        embed(candidate.candidateId());
        assertThat(jdbc.sql("SELECT VECTOR_DIMENSION_COUNT(embedding) FROM event_memories WHERE candidate_id = ?")
                .param(UuidBytes.encode(candidate.candidateId())).query(Integer.class).single())
                .isEqualTo(1536);
    }

    @Test
    void failedEmbeddingRollsBackAndCanBeRetried() {
        CandidateWork failed = candidate("user:demo", "remember retryable memory");
        CandidateWork other = candidate("user:demo", "remember independent memory");
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
            assertThat(hasEmbedding(failed)).isFalse();
            embed(other.candidateId());
            assertThat(hasEmbedding(other)).isTrue();
        } finally {
            jdbc.sql("DROP TRIGGER fail_one_embedding").update();
        }
        embed(failed.candidateId());
        assertThat(hasEmbedding(failed)).isTrue();
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
        assertThat(hasEmbedding(foreign)).isTrue();
    }

    private CandidateWork completed(String owner, String text) {
        CandidateWork candidate = candidate(owner, "remember " + text);
        embed(candidate.candidateId());
        return candidate;
    }

    private CandidateWork candidate(String owner, String message) {
        UUID source = UUID.randomUUID();
        process(new IncomingEvent(source, owner, Map.of("message", message), true));
        return prepare(source).getFirst();
    }

    private boolean hasEmbedding(CandidateWork candidate) {
        return jdbc.sql("SELECT COUNT(*) FROM event_memories WHERE candidate_id = ? AND embedding IS NOT NULL")
                .param(UuidBytes.encode(candidate.candidateId())).query(Integer.class).single() == 1;
    }

    private Transcript process(IncomingEvent event) {
        transaction(connection -> {
            intake.process(connection, event, (topic, key, value) -> {}, "transcripts");
            return null;
        });
        return transcripts.findBySourceEventId(event.sourceEventId()).orElse(null);
    }

    private List<CandidateWork> prepare(UUID sourceEventId) {
        long id = transcripts.findBySourceEventId(sourceEventId).orElseThrow().transcriptId();
        transaction(connection -> {
            preparation.process(connection, id, (topic, key, value) -> {}, "embeddings");
            return null;
        });
        return candidates.findCandidates(sourceEventId);
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
