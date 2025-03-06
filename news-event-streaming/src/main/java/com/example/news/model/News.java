package com.example.news.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class News {
    private String _id = UUID.randomUUID().toString();
    private String article;

    private List<NewsVector> news_vector;
}
