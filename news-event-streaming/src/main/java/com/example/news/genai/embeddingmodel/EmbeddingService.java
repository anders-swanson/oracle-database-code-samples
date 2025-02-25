package com.example.news.genai.embeddingmodel;

import java.util.Collections;
import java.util.List;

import com.example.news.genai.vectorstore.Embedding;
import oracle.sql.VECTOR;

public interface EmbeddingService {
    List<VECTOR> embedAll(List<String> chunks);
    default VECTOR embed(String chunk) {
        return embedAll(Collections.singletonList(chunk)).getFirst();
    }
}
