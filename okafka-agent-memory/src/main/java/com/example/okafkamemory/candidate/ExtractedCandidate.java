package com.example.okafkamemory.candidate;

/** Model output before provenance and a stable ID are attached. */
public record ExtractedCandidate(String evidence, String candidateText) {
}
