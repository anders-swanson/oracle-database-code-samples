package com.example.news.genai.vectorstore;

import java.sql.SQLException;

import oracle.sql.VECTOR;
import org.springframework.stereotype.Component;

@Component
public class VectorDataAdapter {
    /**
     * Convert a float[] to an Oracle VECTOR type.
     * @param vector To convert.
     * @return Converted Oracle VECTOR type.
     * @throws SQLException If the conversion fails.
     */
    VECTOR toVECTOR(float[] vector) throws SQLException {
        return VECTOR.ofFloat64Values(normalize(vector));
    }

    /**
     * Normalizes a vector, converting it to a unit vector with length 1.
     * @param v Vector to normalize.
     * @return The normalized vector.
     */
    private float[] normalize(float[] v) {
        double squaredSum = 0d;

        for (float e : v) {
            squaredSum += e * e;
        }

        final float magnitude = (float) Math.sqrt(squaredSum);

        if (magnitude > 0) {
            final float multiplier = 1f / magnitude;
            final int length = v.length;
            for (int i = 0; i < length; i++) {
                v[i] *= multiplier;
            }
        }

        return v;
    }

    /**
     * Converts a double[] to a float[].
     * @param values Array of double values.
     * @return Converted float[].
     */
    float[] toFloatArray(double[] values) {
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (float) values[i];
        }
        return result;
    }
}
