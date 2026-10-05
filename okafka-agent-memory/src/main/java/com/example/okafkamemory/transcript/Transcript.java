package com.example.okafkamemory.transcript;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record Transcript(
        long transcriptId,
        UUID sourceEventId,
        String ownerScope,
        Map<String, Object> eventPayload,
        OffsetDateTime createdAt) {
}
