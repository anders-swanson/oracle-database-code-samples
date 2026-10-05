package com.example.okafkamemory.transcript;

import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository {
    /**
     * Stores a transcript once per source event ID and returns the existing row on a retry.
     */
    Transcript storeIfAbsent(TranscriptDraft draft);

    Optional<Transcript> findById(long transcriptId);

    Optional<Transcript> findBySourceEventId(UUID sourceEventId);

    Optional<Transcript> findReady(long transcriptId);

}
