package com.example.support.model;

public record SimilarIncident(
        long ticketId,
        String subject,
        String customerName,
        String customerTier,
        String productName,
        String productFamily,
        String slaStatus,
        double score,
        int textScore
) {
}
