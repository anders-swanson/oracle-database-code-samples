package com.example.news.model;

import java.util.UUID;

import lombok.Data;
import oracle.sql.VECTOR;

@Data
public class NewsVector {
    private UUID _id = UUID.randomUUID();
    private UUID news_id;
    private String chunk;
    private VECTOR embedding;
}
