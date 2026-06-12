package com.example.security;

record SupportCaseView(
        long caseId,
        String tenantId,
        String region,
        String assignedAgent,
        String severity,
        String status,
        String subject,
        String customerEmail,
        String ssn,
        String internalNotes,
        String policyReason
) {
}
