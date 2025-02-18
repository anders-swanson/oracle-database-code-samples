package com.example.vectorstore;

import java.sql.SQLException;

import oracle.sql.VECTOR;

class OracleDataAdapter {
    float[] toFloatArray(double[] vector) {
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        return result;
    }

    VECTOR toVECTOR(float[] vector) throws SQLException {
        return VECTOR.ofFloat64Values(normalize(vector));
    }

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
}
