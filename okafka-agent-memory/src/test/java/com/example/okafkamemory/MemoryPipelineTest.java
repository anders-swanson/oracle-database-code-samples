package com.example.okafkamemory;

import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.events.MemoryTopics;
import com.example.okafkamemory.events.TransactionalEventConsumer;
import com.example.okafkamemory.events.TranscriptReady;
import com.example.okafkamemory.intake.IncomingEvent;
import com.example.okafkamemory.intake.IntakeService;
import com.example.okafkamemory.intake.OkafkaIntakeConfiguration;
import com.example.okafkamemory.retrieval.MemorySearchRequest;
import com.example.okafkamemory.retrieval.MemorySearchService;
import com.example.okafkamemory.retrieval.MemorySearchResult;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import com.oracle.spring.testcontainers.OracleContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.oracleucp.connection-pool-name=okafka-agent-memory-end-to-end",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema.sql"
})
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OCI_COMPARTMENT_ID", matches = ".+")
class MemoryPipelineTest {
    @Container @ServiceConnection
    private static final OracleContainer oracle = new OracleContainer()
            .withUsername("TESTUSER").withPassword("Welcome123#");
    private static boolean prepared;

    @Autowired @Qualifier("intakeOkafkaProperties") Properties okafkaProperties;
    @Autowired @Qualifier("intakeConsumer") TransactionalEventConsumer<IncomingEvent> intake;
    @Autowired java.util.List<TransactionalEventConsumer<?>> stages;
    @Autowired MemoryTopics topics;
    @Autowired @Qualifier("embeddingConsumer") TransactionalEventConsumer<?> embedding;
    @Autowired @Qualifier("extractionConsumer") TransactionalEventConsumer<?> extraction;
    @Autowired MemorySearchService search;
    @Autowired IntakeService intakeService;
    @Autowired JdbcClient jdbc;
    @Value("${memory.candidates.score-threshold}") int scoreThreshold;
    @Value("${spring.ai.oci.genai.embedding.dimensions}") int embeddingDimensions;

    @DynamicPropertySource
    static void connection(DynamicPropertyRegistry registry) {
        registry.add("memory.okafka.bootstrap-servers", () -> "localhost:" + preparedPort());
    }

    private static synchronized int preparedPort() {
        if (!prepared) {
            try {
                oracle.start();
                oracle.copyFileToContainer(MountableFile.forClasspathResource("okafka.sql"), "/tmp/okafka.sql");
                var result = oracle.execInContainer("sqlplus", "sys / as sysdba", "@/tmp/okafka.sql");
                assertThat(result.getExitCode()).as(result.getStdout() + result.getStderr()).isZero();
                prepared = true;
            } catch (Exception error) {
                throw new IllegalStateException("Unable to prepare Oracle AI Database Free", error);
            }
        }
        return oracle.getOraclePort();
    }

    @BeforeEach
    void isolateActiveMemories() {
        // Tests share a container, but each must start with its own active corpus.
        jdbc.sql("UPDATE event_memories SET status = 'inactive' WHERE owner_scope = 'user:demo'").update();
    }

    @AfterAll
    void stopWorkers() {
        stages.forEach(TransactionalEventConsumer::stop);
    }

    @Test
    void producerEventReachesOwnerScopedSearch() throws Exception {
        UUID sourceEventId = DemoProducer.publish(okafkaProperties, "MEMORY_INCOMING",
                "My Oracle AI Database application stores UUIDs as RAW(16).");
        assertThat(awaitPreparation(sourceEventId)).isEqualTo("DONE");
        var memories = awaitEmbeddedMemories(sourceEventId);
        assertThat(search.search(new MemorySearchRequest("Oracle AI Database UUID RAW(16)", 50)))
                .extracting(MemorySearchResult::memoryId)
                .containsAll(memories.stream().map(StoredMemory::id).toList());
        assertThat(transcriptCount(sourceEventId)).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT VSIZE(m.id) FROM event_memories m
                JOIN transcripts t ON m.transcript_id = t.transcript_id
                WHERE t.source_event_id = ?
                """).param(UuidBytes.encode(sourceEventId)).query(Integer.class).list())
                .isNotEmpty().allMatch(size -> size == 16);
    }

    @Test
    void failedIntakeWriteIsRedeliveredAfterRollback() throws Exception {
        String topic = "MEMORY_INCOMING_RETRY_TEST";
        OkafkaIntakeConfiguration.ensureTopic(okafkaProperties, topic);
        jdbc.sql("""
                CREATE OR REPLACE TRIGGER fail_transcript_insert
                AFTER INSERT ON transcripts FOR EACH ROW
                BEGIN
                    RAISE_APPLICATION_ERROR(-20004, 'simulated transcript insert failure');
                END;
                """).update();

        UUID sourceEventId;
        var failed = intakeConsumer(topic, "MEMORY_INTAKE_RETRY_TEST");
        try {
            failed.start();
            sourceEventId = DemoProducer.publish(okafkaProperties, topic, "retry this fact");
            await(Duration.ofSeconds(30), "intake event failure", () -> failed.failure() != null);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                    .param(UuidBytes.encode(sourceEventId)).query(Integer.class).single()).isZero();
        } finally {
            failed.stop();
            jdbc.sql("DROP TRIGGER fail_transcript_insert").update();
        }

        var retried = intakeConsumer(topic, "MEMORY_INTAKE_RETRY_TEST");
        try {
            retried.start();
            await(Duration.ofSeconds(30), "retried intake event", () -> retried.processedRecords() == 1);
            assertThat(retried.failure()).isNull();
            assertThat(jdbc.sql("SELECT transcript_id FROM transcripts WHERE source_event_id = ?")
                    .param(UuidBytes.encode(sourceEventId)).query(Long.class).single()).isPositive();
            assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                    .param(UuidBytes.encode(sourceEventId)).query(Integer.class).single()).isEqualTo(1);
        } finally {
            retried.stop();
        }
        try (var committed = newConsumer("MEMORY_INTAKE_RETRY_TEST")) {
            committed.subscribe(java.util.List.of(topic));
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThat(committed.poll(Duration.ofSeconds(1)).isEmpty()).isTrue();
            }
        }
    }

    @Test
    void intakeWritesAndConsumptionShareCommitAndRollback() throws Exception {
        String topic = "MEMORY_INCOMING_ATOMIC_TEST";
        String group = "MEMORY_INTAKE_ATOMIC_TEST";
        OkafkaIntakeConfiguration.ensureTopic(okafkaProperties, topic);
        String output = "MEMORY_ATOMIC_OUTPUT_TEST";
        OkafkaIntakeConfiguration.ensureTopic(okafkaProperties, output);
        UUID id = DemoProducer.publish(okafkaProperties, topic, "I prefer atomic intake");

        try (var consumer = newConsumer(group)) {
            consumer.subscribe(java.util.List.of(topic));
            IncomingEvent event = pollEvent(consumer).value();
            assertThat(event.sourceEventId()).isEqualTo(id);
            try (var producer = transactionalProducer(consumer.getDBConnection())) {
                producer.initTransactions();
                producer.beginTransaction();
                intakeService.process(consumer.getDBConnection(), event, publisher(producer), output);
                assertThat(transcriptCount(id)).isZero();
                producer.abortTransaction();
            }
        }
        assertThat(transcriptCount(id)).isZero();

        try (var consumer = newConsumer(group)) {
            consumer.subscribe(java.util.List.of(topic));
            IncomingEvent redelivered = pollEvent(consumer).value();
            assertThat(redelivered.sourceEventId()).isEqualTo(id);
            try (var producer = transactionalProducer(consumer.getDBConnection())) {
                producer.initTransactions();
                producer.beginTransaction();
                intakeService.process(consumer.getDBConnection(), redelivered, publisher(producer), output);
                producer.commitTransaction();
            }
        }
        try (var outputConsumer = com.example.okafkamemory.events.MemoryPipelineConfiguration.consumer(
                okafkaProperties, output, TranscriptReady.class)) {
            outputConsumer.subscribe(java.util.List.of(output));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            org.apache.kafka.clients.consumer.ConsumerRecords<String, TranscriptReady> outputRecords;
            do {
                outputRecords = outputConsumer.poll(Duration.ofMillis(250));
            } while (outputRecords.isEmpty() && System.nanoTime() < deadline);
            assertThat(outputRecords.count()).isEqualTo(1);
            outputConsumer.commitSync();
            assertThat(outputConsumer.poll(Duration.ofSeconds(1)).isEmpty()).isTrue();
        }
        assertThat(transcriptCount(id)).isEqualTo(1);
        try (var consumer = newConsumer(group)) {
            consumer.subscribe(java.util.List.of(topic));
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThat(consumer.poll(Duration.ofSeconds(1)).isEmpty()).isTrue();
            }
        }
    }

    @Test
    void embeddingFailureIsRetriedFromItsEvent() throws Exception {
        jdbc.sql("""
                CREATE OR REPLACE TRIGGER fail_embedding_event
                BEFORE UPDATE OF embedding ON event_memories FOR EACH ROW
                BEGIN
                    RAISE_APPLICATION_ERROR(-20005,
                            'simulated embedding event failure for ' || RAWTOHEX(:NEW.id));
                END;
                """).update();
        UUID id;
        UUID memoryId;
        try {
            id = DemoProducer.publish(okafkaProperties, topics.incoming(), "My OKafka consumers use consumer.getDBConnection() for transactional writes.");
            assertThat(awaitPreparation(id))
                    .as("The durable fact must be admitted before testing embedding retry")
                    .isEqualTo("DONE");
            memoryId = jdbc.sql("""
                    SELECT m.id FROM event_memories m
                    JOIN transcripts t ON t.transcript_id = m.transcript_id
                    WHERE t.source_event_id = ?
                    """).param(UuidBytes.encode(id))
                    .query((rs, row) -> UuidBytes.decode(rs.getBytes("id"))).single();
            String expectedFailure = "simulated embedding event failure for "
                    + HexFormat.of().withUpperCase().formatHex(UuidBytes.encode(memoryId));
            awaitPipeline("simulated embedding failure", id, () -> jdbc.sql("""
                    SELECT COUNT(*) FROM event_memories WHERE id = ? AND embedding IS NULL
                    """).param(UuidBytes.encode(memoryId)).query(Integer.class).single() == 1
                    && hasMessage(embedding.failure(), expectedFailure));
        } finally {
            jdbc.sql("DROP TRIGGER fail_embedding_event").update();
        }
        awaitPipeline("embedding retry to persist the vector", id, () -> jdbc.sql("""
                SELECT COUNT(*) FROM event_memories WHERE id = ? AND embedding IS NOT NULL
                """).param(UuidBytes.encode(memoryId)).query(Integer.class).single() == 1
                && embedding.failure() == null);
        assertThat(transcriptCount(id)).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM event_memories m
                JOIN transcripts t ON t.transcript_id = m.transcript_id
                WHERE t.source_event_id = ?
                """).param(UuidBytes.encode(id)).query(Integer.class).single()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedExamples")
    void documentedExamplesPassThroughThePipeline(PipelineExample example) throws Exception {
        UUID id = UUID.randomUUID();
        publishIncoming(new IncomingEvent(id, "user:demo", example.payload(), true));
        String status = awaitPreparation(id);
        assertThat(status).as(example.name()).isEqualTo(example.expectedStatus());
        assertThat(transcriptCount(id)).isEqualTo(1);
        assertThat(new JdbcTranscriptRepository(jdbc).findBySourceEventId(id).orElseThrow().eventPayload())
                .isEqualTo(example.payload());
        if ("NO_MEMORY".equals(status)) {
            assertThat(storedMemories(id)).isEmpty();
            return;
        }
        var memories = awaitEmbeddedMemories(id);
        String combinedText = String.join(" ", memories.stream().map(StoredMemory::text).toList());
        for (String term : example.terms()) {
            assertThat(combinedText).as(example.name()).containsIgnoringCase(term);
        }
        for (String term : example.excludedTerms()) {
            assertThat(combinedText).as(example.name()).doesNotContainIgnoringCase(term);
        }
        if (!example.terms().isEmpty()) {
            assertThat(search.search(new MemorySearchRequest(String.join(" ", example.terms()), 50)))
                    .extracting(MemorySearchResult::memoryId)
                    .containsAll(memories.stream().map(StoredMemory::id).toList());
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void intakeDropsInvalidEventsAndAllowsARejectedIdToBeReused(CapturedOutput output) throws Exception {
        var denied = new IncomingEvent(UUID.randomUUID(), "user:demo",
                Map.of("message", "My OKafka consumers use consumer.getDBConnection() for transactional writes."), false);
        var blankOwner = new IncomingEvent(UUID.randomUUID(), " ",
                Map.of("message", "My application uses OKafka with TxEventQ."), true);
        var nullOwner = new IncomingEvent(UUID.randomUUID(), null, Map.of(), true);
        var nullPayload = new IncomingEvent(UUID.randomUUID(), "user:demo", null, true);
        var multipleFailures = new IncomingEvent(UUID.randomUUID(), null, null, false);
        var marker = new IncomingEvent(UUID.randomUUID(), "user:demo", Map.of(), true);
        publishIncoming(denied, blankOwner, nullOwner, nullPayload, multipleFailures, marker);
        // The single-partition marker proves that the preceding rejected inputs were consumed.
        assertThat(awaitPreparation(marker.sourceEventId())).isEqualTo("NO_MEMORY");
        assertThat(storedMemories(marker.sourceEventId())).isEmpty();
        var rejected = List.of(denied, blankOwner, nullOwner, nullPayload, multipleFailures);
        var reasons = List.of("STORAGE_NOT_ALLOWED", "MISSING_OWNER_SCOPE", "MISSING_OWNER_SCOPE",
                "EMPTY_TRANSCRIPT", "STORAGE_NOT_ALLOWED");
        for (int index = 0; index < rejected.size(); index++) {
            UUID id = rejected.get(index).sourceEventId();
            assertThat(transcriptCount(id)).isZero();
            assertThat(storedMemories(id)).isEmpty();
            assertThat(output.getOut()).contains("rejected incoming event " + id + " because " + reasons.get(index));
        }
        publishIncoming(new IncomingEvent(denied.sourceEventId(), "user:demo",
                Map.of("message", "My OKafka consumers use consumer.getDBConnection() for transactional writes."), true));
        assertThat(awaitPreparation(denied.sourceEventId())).isEqualTo("DONE");
        assertThat(awaitEmbeddedMemories(denied.sourceEventId())).isNotEmpty();
        assertThat(transcriptCount(denied.sourceEventId())).isEqualTo(1);
    }

    @Test
    void duplicateSourceIdKeepsTheOriginalTranscriptAndMemories() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> original = Map.of("message", "My OKafka events use OSON serialization.");
        publishIncoming(new IncomingEvent(id, "user:demo", original, true));
        assertThat(awaitPreparation(id)).isEqualTo("DONE");
        var memories = awaitEmbeddedMemories(id);
        assertThat(String.join(" ", memories.stream().map(StoredMemory::text).toList()))
                .containsIgnoringCase("OSON");
        var marker = new IncomingEvent(UUID.randomUUID(), "user:demo", Map.of(), true);
        publishIncoming(new IncomingEvent(id, "user:demo",
                Map.of("message", "My OKafka events use plain text serialization."), true), marker);
        assertThat(awaitPreparation(marker.sourceEventId())).isEqualTo("NO_MEMORY");
        assertThat(transcriptCount(id)).isEqualTo(1);
        assertThat(new JdbcTranscriptRepository(jdbc).findBySourceEventId(id).orElseThrow().eventPayload())
                .isEqualTo(original);
        assertThat(storedMemories(id)).containsExactlyElementsOf(memories);
    }

    @Test
    void correctionEventSupersedesTheOriginalAfterEmbedding() throws Exception {
        UUID originalSource = DemoProducer.publish(okafkaProperties, topics.incoming(),
                "My Oracle AI Database ledger application stores UUIDs as VARCHAR2(36).");
        assertThat(awaitPreparation(originalSource)).isEqualTo("DONE");
        UUID originalMemory = awaitEmbeddedMemories(originalSource).getFirst().id();
        UUID correctionSource = DemoProducer.publish(okafkaProperties, topics.incoming(),
                "My Oracle AI Database ledger application now stores UUIDs as RAW(16).", originalMemory);
        assertThat(awaitPreparation(correctionSource)).isEqualTo("DONE");
        var replacements = awaitEmbeddedMemories(correctionSource);
        assertThat(jdbc.sql("SELECT status FROM event_memories WHERE id = ?")
                .param(UuidBytes.encode(originalMemory)).query(String.class).single()).isEqualTo("inactive");
        assertThat(search.search(new MemorySearchRequest("ledger UUID RAW(16)", 50)))
                .extracting(MemorySearchResult::memoryId)
                .doesNotContain(originalMemory)
                .containsAll(replacements.stream().map(StoredMemory::id).toList());
        assertThat(search.search(new MemorySearchRequest("Suggest a recipe for chocolate cake", 50))).isEmpty();
    }

    private static Stream<PipelineExample> documentedExamples() throws Exception {
        try (var input = MemoryPipelineTest.class.getResourceAsStream("/memory-pipeline-examples.json")) {
            return Stream.of(new ObjectMapper().readValue(input, PipelineExample[].class));
        }
    }

    private record PipelineExample(String name, Map<String, Object> payload, String expectedStatus,
                                   List<String> terms, List<String> excludedTerms) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record StoredMemory(UUID id, String text, String evidence, int score, int dimensions) {
    }

    private String awaitPreparation(UUID id) throws InterruptedException {
        awaitPipeline("candidate admission", id, () -> jdbc.sql("""
                SELECT preparation_status FROM transcripts WHERE source_event_id = ?
                """).param(UuidBytes.encode(id)).query(String.class).optional()
                .filter(status -> !"READY".equals(status)).isPresent());
        return jdbc.sql("SELECT preparation_status FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(id)).query(String.class).single();
    }

    private List<StoredMemory> awaitEmbeddedMemories(UUID id) throws InterruptedException {
        awaitPipeline("memory embeddings", id, () -> {
            var memories = storedMemories(id);
            return !memories.isEmpty() && memories.stream()
                    .allMatch(memory -> memory.dimensions() == embeddingDimensions);
        });
        var memories = storedMemories(id);
        assertThat(memories).allSatisfy(memory -> {
            assertThat(memory.id()).isNotNull();
            assertThat(memory.text()).isNotBlank();
            assertThat(memory.evidence()).isNotBlank();
            assertThat(memory.score()).isGreaterThan(scoreThreshold).isLessThanOrEqualTo(100);
            assertThat(memory.dimensions()).isEqualTo(embeddingDimensions);
        });
        return memories;
    }

    private List<StoredMemory> storedMemories(UUID id) {
        return jdbc.sql("""
                SELECT m.id, m.memory_text, m.evidence, m.judge_score,
                       VECTOR_DIMENSION_COUNT(m.embedding) AS dimensions
                FROM event_memories m
                JOIN transcripts t ON t.transcript_id = m.transcript_id
                WHERE t.source_event_id = ? ORDER BY m.id
                """).param(UuidBytes.encode(id)).query((rs, row) -> new StoredMemory(
                        UuidBytes.decode(rs.getBytes("id")), rs.getString("memory_text"),
                        rs.getString("evidence"), rs.getInt("judge_score"), rs.getInt("dimensions"))).list();
    }

    private void publishIncoming(IncomingEvent... events) throws Exception {
        Properties properties = new Properties();
        properties.putAll(okafkaProperties);
        properties.put("enable.idempotence", "true");
        try (var producer = new org.oracle.okafka.clients.producer.KafkaProducer<String, IncomingEvent>(properties,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new OSONKafkaSerializationFactory(JSONB.createDefault()).createSerializer())) {
            for (var event : events) {
                producer.send(new ProducerRecord<>(topics.incoming(), event.sourceEventId().toString(), event)).get();
            }
        }
    }

    private void awaitPipeline(String operation, UUID id, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        try {
            await(Duration.ofSeconds(30), operation, condition);
        } catch (AssertionError timeout) {
            throw new AssertionError("Timed out waiting for " + operation + "; " + pipelineState(id), timeout);
        }
    }

    private String pipelineState(UUID id) {
        return "Pipeline state: transcripts=" + jdbc.sql("SELECT preparation_status FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(id)).query(String.class).list()
                + ", intake=" + intake.processedRecords() + "/" + intake.failure()
                + ", extraction=" + extraction.processedRecords() + "/" + extraction.failure()
                + ", embedding=" + embedding.processedRecords() + "/" + embedding.failure();
    }

    private int transcriptCount(UUID sourceEventId) {
        return jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                .param(UuidBytes.encode(sourceEventId)).query(Integer.class).single();
    }

    private static ConsumerRecord<String, IncomingEvent> pollEvent(KafkaConsumer<String, IncomingEvent> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                assertThat(records.count()).isEqualTo(1);
                return records.iterator().next();
            }
        }
        throw new AssertionError("Timed out waiting for atomic intake event");
    }

    private TransactionalEventConsumer<IncomingEvent> intakeConsumer(String topic, String group) {
        return new TransactionalEventConsumer<>(topic, newConsumer(group), okafkaProperties,
                (connection, record, publisher) -> intakeService.process(
                        connection, record.value(), publisher, topics.transcripts()));
    }

    private org.oracle.okafka.clients.producer.KafkaProducer<String, Object> transactionalProducer(
            java.sql.Connection connection) {
        Properties properties = new Properties();
        properties.putAll(okafkaProperties);
        properties.put("oracle.transactional.producer", "true");
        properties.put("enable.idempotence", "true");
        return new org.oracle.okafka.clients.producer.KafkaProducer<>(properties,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new OSONKafkaSerializationFactory(JSONB.createDefault()).createSerializer(), connection);
    }

    private EventPublisher publisher(org.oracle.okafka.clients.producer.KafkaProducer<String, Object> producer) {
        return (topic, key, event) -> producer.send(
                new org.apache.kafka.clients.producer.ProducerRecord<>(topic, key, event)).get();
    }

    private static boolean hasMessage(Throwable error, String message) {
        while (error != null) {
            if (error.getMessage() != null && error.getMessage().contains(message)) return true;
            error = error.getCause();
        }
        return false;
    }

    private KafkaConsumer<String, IncomingEvent> newConsumer(String groupId) {
        Properties properties = new Properties();
        properties.putAll(okafkaProperties);
        properties.put("group.id", groupId);
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        return new KafkaConsumer<>(properties, new StringDeserializer(),
                new OSONKafkaSerializationFactory(JSONB.createDefault())
                        .createDeserializer(IncomingEvent.class));
    }

    private static void await(Duration timeout, String operation,
                              java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + operation);
    }
}
