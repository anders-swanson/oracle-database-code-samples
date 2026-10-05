package com.example.okafkamemory.candidate;

import com.example.okafkamemory.events.CandidateReady;
import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.Transcript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
public class CandidatePreparationService {
    private final CandidateExtractor extractor;
    private final SpringAiCandidateJudge judge;
    private final int scoreThreshold;

    public CandidatePreparationService(CandidateExtractor extractor,
                                       SpringAiCandidateJudge judge,
                                       @Value("${memory.candidates.score-threshold}") int scoreThreshold) {
        if (scoreThreshold < 0 || scoreThreshold > 100) {
            throw new IllegalArgumentException("memory.candidates.score-threshold must be between 0 and 100");
        }
        this.extractor = extractor;
        this.judge = judge;
        this.scoreThreshold = scoreThreshold;
    }

    public void process(Connection connection, long transcriptId, EventPublisher publisher,
                        String candidateTopic) throws Exception {
        var transcripts = JdbcTranscriptRepository.from(connection);
        var workRepository = CandidateWorkRepository.from(connection);
        Optional<Transcript> ready = transcripts.findReady(transcriptId);
        if (ready.isEmpty()) return;
        Transcript transcript = ready.orElseThrow();

        var candidates = new ArrayList<CandidateWork>();
        for (ExtractedCandidate extracted : extractor.extract(transcript)) {
            if (extracted == null || extracted.evidence() == null || extracted.candidateText() == null) {
                continue;
            }
            String evidence = extracted.evidence().trim();
            String text = extracted.candidateText().trim();
            if (evidence.isEmpty() || text.isEmpty()) continue;
            int score = judge.score(transcript, new ExtractedCandidate(evidence, text));
            if (score <= scoreThreshold) continue;
            candidates.add(new CandidateWork(UUID.randomUUID(), transcript.sourceEventId(),
                    transcript.transcriptId(), transcript.ownerScope(), evidence, text, score));
        }
        var result = workRepository.complete(transcript, candidates);
        for (CandidateWork candidate : result.candidates()) {
            publisher.publish(candidateTopic, candidate.candidateId().toString(),
                    new CandidateReady(candidate.candidateId()));
        }
    }
}
