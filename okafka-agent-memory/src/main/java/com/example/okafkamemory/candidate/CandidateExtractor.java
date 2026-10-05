package com.example.okafkamemory.candidate;

import com.example.okafkamemory.transcript.Transcript;

import java.util.List;

public interface CandidateExtractor {
    List<ExtractedCandidate> extract(Transcript transcript);
}
