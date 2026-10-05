package com.example.okafkamemory.intake;

import org.springframework.stereotype.Component;

@Component
public class IntakePolicy {
    /** Returns a reason for rejection, or null when storage is allowed. */
    public String rejectionReason(IncomingEvent event) {
        if (!event.storageAllowed()) {
            return "STORAGE_NOT_ALLOWED";
        }
        if (event.ownerScope() == null || event.ownerScope().isBlank()) {
            return "MISSING_OWNER_SCOPE";
        }
        if (event.transcriptPayload() == null) {
            return "EMPTY_TRANSCRIPT";
        }
        return null;
    }
}
