package com.example.okafkamemory.events;

public record MemoryTopics(String incoming, String transcripts, String candidates, String embeddings) {
}
