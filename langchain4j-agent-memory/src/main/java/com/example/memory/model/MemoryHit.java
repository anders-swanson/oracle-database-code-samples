package com.example.memory.model;

public record MemoryHit(
        long id,
        String memoryKind,
        String title,
        String summary,
        String searchText,
        String service,
        String environment,
        String incidentId,
        String changeTicket,
        double vectorScore,
        int textScore,
        double fusedScore,
        String matchedBy
) {
    public String reference() {
        return "M" + id;
    }
}
