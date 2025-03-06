package com.example.news.genai.vectorstore;

import oracle.sql.VECTOR;

public record Embedding(VECTOR vector, String content) {}
