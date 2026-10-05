package com.example.okafkamemory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.stream.IntStream;

/** Word-count vectors make lexical overlap deterministic without OCI calls. */
record FixtureEmbeddingModel(int dimensions) implements EmbeddingModel {
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return new EmbeddingResponse(IntStream.range(0, request.getInstructions().size())
                .mapToObj(index -> new Embedding(vector(request.getInstructions().get(index)), index)).toList());
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    private float[] vector(String text) {
        float[] vector = new float[dimensions];
        for (String term : text.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!term.isBlank()) vector[Math.floorMod(term.hashCode(), dimensions)]++;
        }
        return vector;
    }
}
