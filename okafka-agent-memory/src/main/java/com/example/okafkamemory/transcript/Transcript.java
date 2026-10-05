package com.example.okafkamemory.transcript;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Transcript(
        long transcriptId,
        UUID sourceEventId,
        String ownerScope,
        String eventPayload,
        OffsetDateTime createdAt) {
}
