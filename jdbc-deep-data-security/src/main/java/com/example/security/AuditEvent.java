package com.example.security;

record AuditEvent(
        String actorName,
        String actorRole,
        String securityMode,
        String operation,
        Long caseId,
        int rowsAffected,
        boolean elevated
) {
}
