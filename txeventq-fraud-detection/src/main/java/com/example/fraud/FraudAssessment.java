package com.example.fraud;

/** Persisted, explainable outcome of one card-charge assessment. */
public record FraudAssessment(
        long transactionId,
        double spatialScore,
        double behaviorScore,
        double amountScore,
        double velocityScore,
        double totalScore,
        String decision,
        String reasonCodes
) {
}
