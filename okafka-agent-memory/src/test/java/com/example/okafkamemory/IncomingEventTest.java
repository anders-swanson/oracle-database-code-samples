package com.example.okafkamemory;

import com.example.okafkamemory.intake.IncomingEvent;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncomingEventTest {
    @Test
    void sourceEventUuidAndStructuredPayloadSurviveOsonSerialization() {
        var event = new IncomingEvent(UUID.randomUUID(), "user:demo", Map.of("messages", List.of(
                Map.of("role", "user", "text", "remember I prefer \"dark\" mode\nwith tabs"))), true);
        var factory = new OSONKafkaSerializationFactory(JSONB.createDefault());
        try (var serializer = factory.createSerializer();
             var deserializer = factory.createDeserializer(IncomingEvent.class)) {
            byte[] bytes = serializer.serialize("MEMORY_INCOMING", event);
            assertThat(deserializer.deserialize("MEMORY_INCOMING", bytes)).isEqualTo(event);
        }
    }

    @Test
    void jsonStringPayloadIsRejectedByOsonDeserializer() {
        var factory = new OSONKafkaSerializationFactory(JSONB.createDefault());
        try (var serializer = factory.createSerializer();
             var deserializer = factory.createDeserializer(IncomingEvent.class)) {
            byte[] bytes = serializer.serialize("MEMORY_INCOMING", Map.of(
                    "sourceEventId", UUID.randomUUID().toString(), "ownerScope", "user:demo",
                    "transcriptPayload", "{}", "storageAllowed", true));
            assertThatThrownBy(() -> deserializer.deserialize("MEMORY_INCOMING", bytes))
                    .isInstanceOf(jakarta.json.bind.JsonbException.class);
        }
    }

    @Test
    void sourceEventIdIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IncomingEvent(null, "user:demo", Map.of(), true))
                .withMessage("sourceEventId is required");
    }

    @Test
    void arbitrarySourceEventStringsAreRejected() {
        assertThatThrownBy(() -> new ObjectMapper().readValue("""
                {"sourceEventId":"flow-duplicate","ownerScope":"user:demo",
                 "transcriptPayload":{},"storageAllowed":true}
                """, IncomingEvent.class)).isInstanceOf(JacksonException.class);
    }
}
