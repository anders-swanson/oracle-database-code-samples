package com.example.fraud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;

final class BehaviorVector {
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    static Match getBehaviorVectorDistance(Connection connection, CardChargeEvent event)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select vector_distance(embedding, ?, COSINE) as distance
                from cardholder_behavior_profiles
                where cardholder_id = ?
                order by distance
                fetch first 1 row only
                """)) {
            VECTOR queryVector = normalizedFloat32VECTOR(event.toSemanticString());
            statement.setObject(1, queryVector, OracleType.VECTOR.getVendorTypeNumber());
            statement.setLong(2, event.getCardholderId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("No behavior profile for " + event.getCardholderId());
                }
                return new Match(queryVector.toFloatArray(), resultSet.getDouble("distance"));
            }
        }
    }

    static void addBehaviorProfile(Connection connection, long cardholderId, String profileName,
                                   String behaviorDescription) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                merge into cardholder_behavior_profiles target
                using (select ? as cardholder_id, ? as profile_name, ? as embedding from dual) source
                on (target.cardholder_id = source.cardholder_id and target.profile_name = source.profile_name)
                when matched then update set target.embedding = source.embedding
                when not matched then insert (cardholder_id, profile_name, embedding)
                    values (source.cardholder_id, source.profile_name, source.embedding)
                """)) {
            statement.setLong(1, cardholderId);
            statement.setString(2, profileName);
            statement.setObject(3, normalizedFloat32VECTOR(behaviorDescription),
                    OracleType.VECTOR.getVendorTypeNumber());
            statement.executeUpdate();
        }
    }

    static VECTOR normalizedFloat32VECTOR(String text) throws SQLException {
        float[] values = embeddingModel.embed(text)
                .content()
                .vector();
        return VECTOR.ofFloat32Values(normalize(values));
    }

    static float[] normalize(float[] values) {
        double sum = 0;
        for (float value : values) {
            sum += value * value;
        }
        float magnitude = (float) Math.sqrt(sum);
        for (int index = 0; index < values.length; index++) {
            values[index] /= magnitude;
        }
        return values;
    }

    record Match(float[] values, double distance) {
    }
}
