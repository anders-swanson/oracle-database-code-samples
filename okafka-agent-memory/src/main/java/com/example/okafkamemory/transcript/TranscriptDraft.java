package com.example.okafkamemory.transcript;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TranscriptDraft(UUID sourceEventId, String ownerScope, Map<String, Object> eventPayload) {
    public TranscriptDraft {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        requireText(ownerScope, "ownerScope");
        Objects.requireNonNull(eventPayload, "eventPayload");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
