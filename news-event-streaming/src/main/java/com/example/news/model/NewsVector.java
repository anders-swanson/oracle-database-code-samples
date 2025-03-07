package com.example.news.model;

import java.util.UUID;

import lombok.Data;

@Data
public class NewsVector {
    private String id = UUID.randomUUID().toString();
    private String chunk;
    private float[] embedding;
}
