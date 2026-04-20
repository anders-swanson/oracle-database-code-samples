package com.example.memory;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import oracle.sql.VECTOR;

import java.sql.SQLException;

public final class EmbeddingClient {
    private static final int DIMENSIONS = 1536;

    private final OpenAiEmbeddingModel model;

    public EmbeddingClient(String apiKey) {
        this.model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();
    }

    public int dimensions() {
        return DIMENSIONS;
    }

    public float[] embed(String text) {
        Embedding embedding = model.embed(text).content();
        return normalize(embedding.vector());
    }

    public VECTOR embedToVECTOR(String text) {
        return toVector(embed(text));
    }

    public static VECTOR toVector(float[] raw) {
        try {
            return VECTOR.ofFloat32Values(normalize(raw));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static float[] normalize(float[] vector) {
        float[] copy = vector.clone();
        double squaredSum = 0.0d;
        for (float value : copy) {
            squaredSum += value * value;
        }
        double magnitude = Math.sqrt(squaredSum);
        if (magnitude == 0.0d) {
            return copy;
        }
        for (int i = 0; i < copy.length; i++) {
            copy[i] /= (float) magnitude;
        }
        return copy;
    }
}
