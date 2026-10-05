package com.example.okafkamemory.candidate;

import java.util.List;
import java.util.UUID;

public record CandidatePreparationResult(UUID sourceEventId, Outcome outcome,
                                         List<CandidateWork> candidates) {
    public enum Outcome { NO_MEMORY, CANDIDATES }

    public CandidatePreparationResult {
        candidates = List.copyOf(candidates);
    }
}
