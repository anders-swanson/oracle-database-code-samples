package com.example.okafkamemory.retrieval;

import java.util.Map;
import java.util.UUID;

public record MemorySearchResult(UUID memoryId, String memoryText, Map<String, String> metadata,
                                 double score, double vectorScore, double textScore,
                                 double recencyScore) {
}
