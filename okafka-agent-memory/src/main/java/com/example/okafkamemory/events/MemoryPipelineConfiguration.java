package com.example.okafkamemory.events;

import com.example.okafkamemory.candidate.CandidatePreparationService;
import com.example.okafkamemory.embedding.MemoryEmbeddingService;
import com.example.okafkamemory.intake.IncomingEvent;
import com.example.okafkamemory.intake.IntakeService;
import com.example.okafkamemory.intake.OkafkaIntakeConfiguration;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

@Configuration
@ConditionalOnProperty(name = "memory.background-processing.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryPipelineConfiguration {
    @Bean
    MemoryTopics memoryTopics(@Qualifier("intakeOkafkaProperties") Properties properties,
                              @Value("${memory.intake.topic}") String incoming,
                              @Value("${memory.events.transcripts}") String transcripts,
                              @Value("${memory.events.embeddings}") String embeddings) {
        for (String topic : List.of(incoming, transcripts, embeddings)) {
            OkafkaIntakeConfiguration.ensureTopic(properties, topic);
        }
        return new MemoryTopics(incoming, transcripts, embeddings);
    }

    @Bean
    TransactionalEventConsumer<IncomingEvent> intakeConsumer(MemoryTopics topics, IntakeService intake,
            @Qualifier("intakeOkafkaProperties") Properties properties) {
        return new TransactionalEventConsumer<>(topics.incoming(), consumer(properties, topics.incoming(), IncomingEvent.class),
                properties, (connection, record, publisher) -> {
                    IncomingEvent event = Objects.requireNonNull(record.value(), "incoming event is required");
                    requireKey(record.key(), event.sourceEventId().toString());
                    intake.process(connection, event, publisher, topics.transcripts());
                });
    }

    @Bean
    TransactionalEventConsumer<TranscriptReady> extractionConsumer(MemoryTopics topics, CandidatePreparationService preparation,
            @Qualifier("intakeOkafkaProperties") Properties properties) {
        return new TransactionalEventConsumer<>(topics.transcripts(), consumer(properties, topics.transcripts(), TranscriptReady.class),
                properties, (connection, record, publisher) -> {
                    requireKey(record.key(), Long.toString(record.value().transcriptId()));
                    preparation.process(connection, record.value().transcriptId(), publisher, topics.embeddings());
                });
    }

    @Bean
    TransactionalEventConsumer<MemoryReadyForEmbedding> embeddingConsumer(MemoryTopics topics, MemoryEmbeddingService embeddings,
            @Qualifier("intakeOkafkaProperties") Properties properties) {
        return new TransactionalEventConsumer<>(topics.embeddings(), consumer(properties, topics.embeddings(), MemoryReadyForEmbedding.class),
                properties, (connection, record, publisher) -> {
                    requireKey(record.key(), record.value().candidateId().toString());
                    embeddings.embed(connection, record.value().candidateId());
                });
    }

    public static <T> KafkaConsumer<String, T> consumer(Properties base, String topic, Class<T> eventType) {
        Properties properties = new Properties();
        properties.putAll(base);
        properties.put("group.id", topic + "_PROCESSOR");
        properties.put("client.id", "memory-" + topic);
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        // Keep a failed model call isolated to one event and its next-stage writes.
        properties.put("max.poll.records", "1");
        return new KafkaConsumer<>(properties, new StringDeserializer(),
                new OSONKafkaSerializationFactory(JSONB.createDefault()).createDeserializer(eventType));
    }

    private static void requireKey(String actual, String expected) {
        if (!Objects.equals(actual, expected)) throw new IllegalArgumentException("Event key must match its ID");
    }
}
