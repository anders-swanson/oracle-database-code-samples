package com.example.support;

import java.sql.SQLException;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import oracle.sql.VECTOR;
import org.springframework.stereotype.Service;

@Service
class VectorService {
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2EmbeddingModel();

    VECTOR embed(String text) throws SQLException {
        return VECTOR.ofFloat32Values(normalize(EMBEDDING_MODEL.embed(text).content().vector()));
    }

    private static float[] normalize(float[] vector) {
        float[] copy = vector.clone();
        double squaredSum = 0d;
        for (float value : copy) {
            squaredSum += value * value;
        }
        double magnitude = Math.sqrt(squaredSum);
        if (magnitude == 0d) {
            return copy;
        }
        for (int i = 0; i < copy.length; i++) {
            copy[i] /= (float) magnitude;
        }
        return copy;
    }
}
