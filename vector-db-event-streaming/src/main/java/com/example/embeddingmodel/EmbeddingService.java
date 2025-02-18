package com.example.embeddingmodel;

import java.util.Collections;
import java.util.List;

import com.example.model.Embedding;

public interface EmbeddingService {
    List<Embedding> embedAll(List<String> chunks);
    default Embedding embed(String chunk) {
        return embedAll(Collections.singletonList(chunk)).getFirst();
    }
}
