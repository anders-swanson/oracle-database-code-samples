package com.example.okafkamemory.transcript;

import java.util.Objects;
import java.util.UUID;

public record TranscriptDraft(UUID sourceEventId, String ownerScope, String eventPayload) {
    public TranscriptDraft {
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        requireText(ownerScope, "ownerScope");
        Objects.requireNonNull(eventPayload, "eventPayload");
        if (eventPayload.isBlank()) {
            throw new IllegalArgumentException("eventPayload must not be blank");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
