package com.example.okafkamemory.memory;

import java.util.UUID;

/** Admitted memory with its source provenance and judge score. */
public record Memory(UUID memoryId, long transcriptId, String ownerScope,
                     String evidence, String memoryText, int judgeScore) {
}
