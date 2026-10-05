package com.example.okafkamemory.transcript;

import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository {
    /**
     * Returns the newly inserted transcript, or empty when the source event ID already exists.
     */
    Optional<Transcript> insertIfAbsent(TranscriptDraft draft);

    Optional<Transcript> findById(long transcriptId);

    Optional<Transcript> findBySourceEventId(UUID sourceEventId);

    Optional<Transcript> findReady(long transcriptId);

}
