package com.example.okafkamemory.intake;

import java.util.UUID;

public record IntakeResult(UUID sourceEventId, Outcome outcome, String reason, Long transcriptId) {
    public enum Outcome {
        FILTERED,
        ACCEPTED
    }
}
