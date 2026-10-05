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
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import com.oracle.spring.testcontainers.OracleContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
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
class MemoryPipelineIT {
    @Container @ServiceConnection
    private static final OracleContainer oracle = new OracleContainer()
            .withUsername("TESTUSER").withPassword("Welcome123#");
    private static boolean prepared;

    @Autowired @Qualifier("intakeOkafkaProperties") Properties okafkaProperties;
    @Autowired @Qualifier("intakeConsumer") TransactionalEventConsumer<IncomingEvent> intake;
    @Autowired java.util.List<TransactionalEventConsumer<?>> stages;
    @Autowired MemoryTopics topics;
    @Autowired @Qualifier("embeddingConsumer") TransactionalEventConsumer<?> embedding;
    @Autowired MemorySearchService search;
    @Autowired IntakeService intakeService;
    @Autowired JdbcClient jdbc;

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

    @AfterAll
    void stopWorkers() {
        stages.forEach(TransactionalEventConsumer::stop);
    }

    @Test
    void producerEventReachesOwnerScopedSearch() throws Exception {
        UUID sourceEventId = DemoProducer.publish(okafkaProperties, "MEMORY_INCOMING",
                "my favorite color is amber");
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            var hits = search.search(new MemorySearchRequest("favorite color amber", 5));
            if (hits.stream().anyMatch(hit -> hit.memoryText().toLowerCase(java.util.Locale.ROOT).contains("amber"))) {
                assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                        .param(UuidBytes.encode(sourceEventId)).query(Integer.class).single()).isEqualTo(1);
                assertThat(jdbc.sql("""
                        SELECT preparation_status FROM transcripts WHERE source_event_id = ?
                        """).param(UuidBytes.encode(sourceEventId)).query(String.class).single()).isEqualTo("DONE");
                assertThat(jdbc.sql("""
                        SELECT c.status FROM candidate_work c JOIN transcripts t
                        ON c.transcript_id = t.transcript_id WHERE t.source_event_id = ?
                        """).param(UuidBytes.encode(sourceEventId)).query(String.class).single()).isEqualTo("COMPLETE");
                assertThat(jdbc.sql("""
                        SELECT VSIZE(m.id) FROM event_memories m
                        JOIN candidate_work c ON m.candidate_id = c.candidate_id
                        JOIN transcripts t ON c.transcript_id = t.transcript_id
                        WHERE t.source_event_id = ? AND m.embedding IS NOT NULL
                        """).param(UuidBytes.encode(sourceEventId)).query(Integer.class).single()).isEqualTo(16);
                return;
            }
            if (intake.failure() != null) throw new AssertionError("Intake failed", intake.failure());
            Thread.sleep(2000);
        }
        throw new AssertionError("Timed out waiting for producer event to reach search");
    }

    @Test
    void failedIntakeWriteIsRedeliveredAfterRollback() throws Exception {
        String topic = "MEMORY_INCOMING_RETRY_TEST";
        OkafkaIntakeConfiguration.ensureTopic(okafkaProperties, topic);
        jdbc.sql("""
                CREATE OR REPLACE TRIGGER fail_intake_completion
                BEFORE UPDATE OF outcome ON intake_outcomes FOR EACH ROW
                BEGIN
                    IF :NEW.outcome = 'ACCEPTED' THEN
                        RAISE_APPLICATION_ERROR(-20004, 'simulated failure after transcript insert');
                    END IF;
                END;
                """).update();

        UUID sourceEventId;
        var failed = intakeConsumer(topic, "MEMORY_INTAKE_RETRY_TEST");
        try {
            failed.start();
            sourceEventId = DemoProducer.publish(okafkaProperties, topic, "retry this fact");
            await(() -> failed.failure() != null);
            assertThat(receiptCount(sourceEventId)).isZero();
            assertThat(jdbc.sql("SELECT COUNT(*) FROM transcripts WHERE source_event_id = ?")
                    .param(UuidBytes.encode(sourceEventId)).query(Integer.class).single()).isZero();
        } finally {
            failed.stop();
            jdbc.sql("DROP TRIGGER fail_intake_completion").update();
        }

        var retried = intakeConsumer(topic, "MEMORY_INTAKE_RETRY_TEST");
        try {
            retried.start();
            await(() -> retried.processedRecords() == 1);
            assertThat(retried.failure()).isNull();
            assertThat(jdbc.sql("SELECT transcript_id FROM intake_outcomes WHERE source_event_id = ?")
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
                assertThat(receiptCount(id)).isZero();
                assertThat(transcriptCount(id)).isZero();
                producer.abortTransaction();
            }
        }
        assertThat(receiptCount(id)).isZero();
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
        assertThat(receiptCount(id)).isEqualTo(1);
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
                BEFORE UPDATE OF embedding ON event_memories
                BEGIN
                    RAISE_APPLICATION_ERROR(-20005, 'simulated embedding event failure');
                END;
                """).update();
        UUID id;
        try {
            id = DemoProducer.publish(okafkaProperties, topics.incoming(), "I prefer resilient memory processing");
            await(() -> jdbc.sql("""
                    SELECT COUNT(*) FROM event_memories m JOIN candidate_work c
                    ON c.candidate_id = m.candidate_id JOIN transcripts t ON t.transcript_id = c.transcript_id
                    WHERE t.source_event_id = ? AND m.embedding IS NULL AND c.status = 'PENDING_EMBEDDING'
                    """).param(UuidBytes.encode(id)).query(Integer.class).single() == 1
                    && embedding.failure() != null);
        } finally {
            jdbc.sql("DROP TRIGGER fail_embedding_event").update();
        }
        await(() -> jdbc.sql("""
                SELECT COUNT(*) FROM event_memories m JOIN candidate_work c
                ON c.candidate_id = m.candidate_id JOIN transcripts t ON t.transcript_id = c.transcript_id
                WHERE t.source_event_id = ? AND m.embedding IS NOT NULL AND c.status = 'COMPLETE'
                """).param(UuidBytes.encode(id)).query(Integer.class).single() == 1);
    }

    private int receiptCount(UUID sourceEventId) {
        return jdbc.sql("SELECT COUNT(*) FROM intake_outcomes WHERE source_event_id = ?")
                .param(UuidBytes.encode(sourceEventId)).query(Integer.class).single();
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

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for OKafka intake");
    }
}
