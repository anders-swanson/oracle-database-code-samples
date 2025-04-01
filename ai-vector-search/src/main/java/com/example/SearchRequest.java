package com.example;

import java.util.Objects;

public class SearchRequest {
    private final String text;
    private final float[] vector;
    private final int maxResults;
    private final double minScore;

    public SearchRequest(String text, float[] vector) {
        this(text, vector, null, null);
    }

    public SearchRequest(String text, float[] vector, Integer maxResults, Double minScore) {
        this.text = text;
        this.vector = vector;
        this.maxResults = Objects.requireNonNullElse(maxResults, 1);
        this.minScore = Objects.requireNonNullElse(minScore, 0.0);
    }

    public String getText() {
        return text;
    }

    public float[] getVector() {
        return vector;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public double getMinScore() {
        return minScore;
    }
}
