package com.example.vectorstore;

import java.util.Objects;

import lombok.Builder;
import lombok.Getter;

@Getter
public class SearchRequest {
    private final String text;
    private final float[] vector;
    private final int maxResults;
    private final double minScore;

    @Builder
    public SearchRequest(String text, float[] vector, Integer maxResults, Double minScore) {
        this.text = text;
        this.vector = vector;
        this.maxResults = Objects.requireNonNullElse(maxResults, 1);
        this.minScore = Objects.requireNonNullElse(minScore, 0.0);
    }
}
