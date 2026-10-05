package com.example.okafkamemory.candidate;

import java.util.UUID;

/** Independently addressable work for the curation stage. */
public record CandidateWork(UUID candidateId, UUID sourceEventId, long transcriptId,
                            String ownerScope, String evidence, String candidateText) {
}
