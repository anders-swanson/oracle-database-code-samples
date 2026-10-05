package com.example.okafkamemory.candidate;

import java.util.UUID;

/** Admitted candidate with its source provenance and judge score. */
public record CandidateWork(UUID candidateId, UUID sourceEventId, long transcriptId,
                            String ownerScope, String evidence, String candidateText, int judgeScore) {
}
