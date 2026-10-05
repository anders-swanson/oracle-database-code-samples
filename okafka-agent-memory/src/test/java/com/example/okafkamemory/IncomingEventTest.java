package com.example.okafkamemory;

import com.example.okafkamemory.intake.IncomingEvent;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncomingEventTest {
    @Test
    void sourceEventUuidSurvivesOsonSerialization() {
        var event = new IncomingEvent(UUID.randomUUID(), "user:demo", "{}", true);
        var factory = new OSONKafkaSerializationFactory(JSONB.createDefault());
        try (var serializer = factory.createSerializer();
             var deserializer = factory.createDeserializer(IncomingEvent.class)) {
            byte[] bytes = serializer.serialize("MEMORY_INCOMING", event);
            assertThat(deserializer.deserialize("MEMORY_INCOMING", bytes)).isEqualTo(event);
        }
    }

    @Test
    void sourceEventIdIsRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IncomingEvent(null, "user:demo", "{}", true))
                .withMessage("sourceEventId is required");
    }

    @Test
    void arbitrarySourceEventStringsAreRejected() {
        assertThatThrownBy(() -> new ObjectMapper().readValue("""
                {"sourceEventId":"flow-duplicate","ownerScope":"user:demo",
                 "transcriptPayload":"{}","storageAllowed":true}
                """, IncomingEvent.class)).isInstanceOf(JacksonException.class);
    }
}
