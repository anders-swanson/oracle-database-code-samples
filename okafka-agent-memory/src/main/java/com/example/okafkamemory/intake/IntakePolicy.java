package com.example.okafkamemory.intake;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class IntakePolicy {
    private final ObjectMapper json = new ObjectMapper();

    /** Returns a durable reason for rejection, or null when storage is allowed. */
    public String rejectionReason(IncomingEvent event) {
        if (!event.storageAllowed()) {
            return "STORAGE_NOT_ALLOWED";
        }
        if (event.ownerScope() == null || event.ownerScope().isBlank()) {
            return "MISSING_OWNER_SCOPE";
        }
        if (event.transcriptPayload() == null || event.transcriptPayload().isBlank()) {
            return "EMPTY_TRANSCRIPT";
        }
        try {
            JsonNode payload = json.readTree(event.transcriptPayload());
            if (payload == null || !payload.isObject()) {
                return "INVALID_TRANSCRIPT_JSON";
            }
        } catch (JacksonException invalidJson) {
            return "INVALID_TRANSCRIPT_JSON";
        }
        return null;
    }
}
