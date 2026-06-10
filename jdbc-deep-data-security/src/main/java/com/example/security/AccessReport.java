package com.example.security;

import java.util.List;

record AccessReport(
        SecurityMode effectiveMode,
        boolean applicationContextCreated,
        List<SupportCaseView> aliceCases,
        List<SupportCaseView> bobCases,
        List<SupportCaseView> mariaCases,
        int aliceAssignedUpdateRows,
        int aliceUpdateRows,
        int bobUpdateRows,
        int mariaUpdateRows,
        List<SupportCaseView> serviceBeforeElevation,
        List<SupportCaseView> serviceDuringElevation,
        List<SupportCaseView> serviceAfterElevation,
        List<AuditEvent> auditEvents
) {
    void validateExpectedResults() {
        require(caseIds(aliceCases).equals(List.of(1001L, 1002L)), "Alice should see only her assigned ACME West cases");
        require(aliceCases.stream().allMatch(AccessReport::isMasked), "Alice should receive masked sensitive values");

        require(caseIds(bobCases).equals(List.of(1003L)), "Bob should see only his assigned ACME East case");
        require(bobCases.stream().allMatch(AccessReport::isMasked), "Bob should receive masked sensitive values");

        require(caseIds(mariaCases).equals(List.of(1001L, 1002L, 1005L)), "Maria should see ACME West regional cases");
        require(mariaCases.stream().allMatch(AccessReport::isUnmasked), "Maria should receive manager-visible sensitive values");

        require(aliceAssignedUpdateRows == 1, "Alice should update one assigned case");
        require(aliceUpdateRows == 0, "Alice should not update an unassigned case");
        require(bobUpdateRows == 0, "Bob should not update Alice's case");
        require(mariaUpdateRows == 1, "Maria should update one ACME West case");

        require(serviceBeforeElevation.isEmpty(), "Service actor should see no rows before elevation");
        require(caseIds(serviceDuringElevation).equals(List.of(1004L)), "Service elevation should expose only the routed critical case");
        require(serviceAfterElevation.isEmpty(), "Service actor should see no rows after elevation is cleared");

        require(hasAudit("alice@example.com", "UPDATE_STATUS", 1001L, 1, false), "Alice allowed update should be audited");
        require(hasAudit("alice@example.com", "UPDATE_STATUS", 1005L, 0, false), "Alice denied update should be audited");
        require(hasAudit("bob@example.com", "UPDATE_STATUS", 1001L, 0, false), "Bob denied update should be audited");
        require(hasAudit("maria@example.com", "UPDATE_STATUS", 1005L, 1, false), "Maria allowed update should be audited");
        require(hasAudit("router-service", "SELECT_CASES", null, 1, true), "Service elevation should be audited");
    }

    private boolean hasAudit(String actorName, String operation, Long caseId, int rowsAffected, boolean elevated) {
        return auditEvents.stream().anyMatch(event ->
                actorName.equals(event.actorName())
                        && operation.equals(event.operation())
                        && (caseId == null ? event.caseId() == null : caseId.equals(event.caseId()))
                        && rowsAffected == event.rowsAffected()
                        && elevated == event.elevated()
        );
    }

    private static List<Long> caseIds(List<SupportCaseView> cases) {
        return cases.stream().map(SupportCaseView::caseId).toList();
    }

    private static boolean isMasked(SupportCaseView supportCase) {
        return supportCase.customerEmail().startsWith("masked-")
                && supportCase.ssn().startsWith("***-**-")
                && "[redacted by policy]".equals(supportCase.internalNotes());
    }

    private static boolean isUnmasked(SupportCaseView supportCase) {
        return !supportCase.customerEmail().startsWith("masked-")
                && !supportCase.ssn().startsWith("***-**-")
                && !supportCase.internalNotes().startsWith("[redacted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
