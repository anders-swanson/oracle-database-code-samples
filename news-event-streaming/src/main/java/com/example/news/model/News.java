package com.example.news.model;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import lombok.Data;
import oracle.sql.VECTOR;

@Data
public class News {
    private UUID _id = UUID.randomUUID();
    private String highlight;
    private String article;

    private List<NewsVector> embeddings;

    public void setEmbedding(VECTOR embedding) {
        this.embedding = new NewsVector();
        this.embedding.setNews_id(_id);
        this.embedding.setEmbedding(embedding);
    }
}
