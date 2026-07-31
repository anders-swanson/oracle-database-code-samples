package com.example.fraud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import oracle.jdbc.OracleTypes;
import oracle.spatial.geometry.JGeometry;

/** Scores a persisted card charge and stores its fraud assessment. */
public class FraudScoringService {
    private static final double SCORE_THRESHOLD_REVIEW = 40d;
    private static final double SCORE_THRESHOLD_DECLINE = 70d;
    private static final double NORMAL_BEHAVIOR_DISTANCE = .10d;
    private static final double UNUSUAL_BEHAVIOR_DISTANCE = .45d;
    public FraudAssessment score(Connection connection, CardChargeEvent event) throws SQLException {
        CardholderProfile cardholder = cardholder(connection, event.getCardholderId());
        BehaviorVector.Match behaviorMatch = BehaviorVector.getBehaviorVectorDistance(connection, event);
        Instant occurredAt = Instant.parse(event.getOccurredAt());

        double spatialScore = spatialScore(connection, event, occurredAt);
        double behaviorScore = behaviorScore(behaviorMatch.distance());
        double amountScore = amountScore(event.getAmount(), cardholder.normalAmount());
        double velocityScore = velocityScore(connection, event.getCardholderId(), occurredAt);

        // compute overall fraud score
        double totalScore = spatialScore * .40 + behaviorScore * .30 + amountScore * .20 + velocityScore * .10;

        String decision = totalScore >= SCORE_THRESHOLD_DECLINE ? "DECLINE"
                : totalScore >= SCORE_THRESHOLD_REVIEW ? "REVIEW" : "APPROVE";
        String reasons = reasonCodes(spatialScore, behaviorScore, amountScore, velocityScore);

        FraudAssessment assessment = new FraudAssessment(event.getTransactionId(), spatialScore, behaviorScore,
                amountScore, velocityScore, totalScore, decision, reasons);
        persistAssessment(connection, assessment);
        return assessment;
    }

    private CardholderProfile cardholder(Connection connection, long cardholderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select normal_amount
                from cardholders
                where cardholder_id = ?
                """)) {
            statement.setLong(1, cardholderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Unknown cardholder " + cardholderId);
                }
                return new CardholderProfile(resultSet.getDouble(1));
            }
        }
    }

    private double spatialScore(Connection connection, CardChargeEvent event, Instant occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select sdo_geom.sdo_distance(location, ?, 0.005, 'unit=KM')
                from card_transactions t
                join fraud_assessments a on a.transaction_id = t.transaction_id
                where t.cardholder_id = ?
                  and a.decision = 'APPROVE'
                  and t.occurred_at < ?
                  and t.occurred_at >= ? - interval '2' hour
                order by t.occurred_at desc
                fetch first 1 row only
                """)) {
            statement.setObject(1, JGeometry.storeJS(point(event), connection), OracleTypes.STRUCT);
            statement.setLong(2, event.getCardholderId());
            statement.setObject(3, asTimestamp(occurredAt));
            statement.setObject(4, asTimestamp(occurredAt));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Math.min(100d, resultSet.getDouble(1) / 5d) : 0d;
            }
        }
    }

    private double amountScore(double amount, double normalAmount) {
        return Math.clamp(((amount / normalAmount) - 1d) * 25d, 0d, 100d);
    }

    private double behaviorScore(double cosineDistance) {
        // Semantic embedding distances are concentrated below 0.5. Map the observed normal-to-unusual range to risk.
        return Math.clamp((cosineDistance - NORMAL_BEHAVIOR_DISTANCE)
                / (UNUSUAL_BEHAVIOR_DISTANCE - NORMAL_BEHAVIOR_DISTANCE) * 100d, 0d, 100d);
    }

    private double velocityScore(Connection connection, long cardholderId, Instant occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from card_transactions
                where cardholder_id = ?
                  and occurred_at < ?
                  and occurred_at >= ? - interval '15' minute
                """)) {
            statement.setLong(1, cardholderId);
            statement.setObject(2, asTimestamp(occurredAt));
            statement.setObject(3, asTimestamp(occurredAt));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return Math.clamp((resultSet.getInt(1) - 2) * (100d / 3d), 0d, 100d);
            }
        }
    }

    private void persistAssessment(Connection connection, FraudAssessment assessment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into fraud_assessments (
                    transaction_id, spatial_score, behavior_score, amount_score, velocity_score,
                    total_score, decision, reason_codes
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, assessment.transactionId());
            statement.setDouble(2, assessment.spatialScore());
            statement.setDouble(3, assessment.behaviorScore());
            statement.setDouble(4, assessment.amountScore());
            statement.setDouble(5, assessment.velocityScore());
            statement.setDouble(6, assessment.totalScore());
            statement.setString(7, assessment.decision());
            statement.setString(8, assessment.reasonCodes());
            statement.executeUpdate();
        }
    }

    private String reasonCodes(double spatial, double behavior, double amount, double velocity) {
        List<String> reasons = new ArrayList<>();
        if (spatial >= 50d) reasons.add("DISTANT_RECENT_TRANSACTION");
        if (behavior >= 50d) reasons.add("UNUSUAL_BEHAVIOR");
        if (amount >= 50d) reasons.add("UNUSUAL_AMOUNT");
        if (velocity >= 50d) reasons.add("HIGH_VELOCITY");
        return reasons.isEmpty() ? "NORMAL_PATTERN" : String.join(",", reasons);
    }

    private JGeometry point(CardChargeEvent event) {
        return JGeometry.createPoint(new double[]{event.getLongitude(), event.getLatitude()}, 2, 8307);
    }

    private OffsetDateTime asTimestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record CardholderProfile(double normalAmount) {
    }
}
