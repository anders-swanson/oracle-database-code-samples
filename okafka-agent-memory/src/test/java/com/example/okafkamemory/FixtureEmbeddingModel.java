package com.example.okafkamemory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.stream.IntStream;

/** Constant test vectors isolate persistence, filtering, and retry behavior from OCI. */
record FixtureEmbeddingModel(int dimensions) implements EmbeddingModel {
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                .mapToObj(index -> new Embedding(vector(), index)).toList());
    }

    @Override
    public float[] embed(Document document) {
        return vector();
    }

    private float[] vector() {
        float[] vector = new float[dimensions];
        vector[0] = 1;
        return vector;
    }
}
