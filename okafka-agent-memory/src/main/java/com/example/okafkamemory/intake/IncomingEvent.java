package com.example.okafkamemory.intake;

import java.util.UUID;

/** Payload received from the incoming TxEventQ topic. */
public record IncomingEvent(UUID sourceEventId, String ownerScope,
                            String transcriptPayload, boolean storageAllowed) {
    public IncomingEvent {
        if (sourceEventId == null) {
            throw new IllegalArgumentException("sourceEventId is required");
        }
    }
}
