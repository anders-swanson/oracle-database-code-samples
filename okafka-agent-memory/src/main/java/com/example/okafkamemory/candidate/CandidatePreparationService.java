package com.example.okafkamemory.candidate;

import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.events.MemoryReadyForEmbedding;
import com.example.okafkamemory.memory.JdbcMemoryRepository;
import com.example.okafkamemory.memory.Memory;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.Transcript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
public class CandidatePreparationService {
    private static final Logger log = LoggerFactory.getLogger(CandidatePreparationService.class);
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
                        String embeddingTopic) throws Exception {
        var transcripts = JdbcTranscriptRepository.from(connection);
        Optional<Transcript> ready = transcripts.findReady(transcriptId);
        if (ready.isEmpty()) {
            log.info("At the candidate extraction stage: skipped transcript {} because it is missing or has already been prepared.", transcriptId);
            return;
        }
        Transcript transcript = ready.orElseThrow();

        log.info("At the candidate extraction stage: asking the model to extract memory candidates from transcript {}.", transcriptId);
        var candidates = extractor.extract(transcript);
        log.info("At the candidate scoring stage: extracted {} candidate(s) from transcript {} and will judge their scores. Only scores above {} will be kept.",
                candidates.size(), transcriptId, scoreThreshold);
        var admitted = new ArrayList<Memory>();
        for (ExtractedCandidate extracted : candidates) {
            if (extracted == null || extracted.evidence() == null || extracted.candidateText() == null) {
                continue;
            }
            String evidence = extracted.evidence().trim();
            String text = extracted.candidateText().trim();
            if (evidence.isEmpty() || text.isEmpty()) continue;
            int score = judge.score(transcript, new ExtractedCandidate(evidence, text));
            log.info("At the candidate scoring stage: judged a candidate from transcript {} with score {}. "
                            + "The score must be above {} to be kept. Decision: {}.",
                    transcriptId, score, scoreThreshold, score > scoreThreshold ? "accepted" : "rejected");
            if (score <= scoreThreshold) continue;
            admitted.add(new Memory(UUID.randomUUID(), transcript.transcriptId(),
                    transcript.ownerScope(), evidence, text, score));
        }
        if (!transcripts.completePreparation(transcriptId, !admitted.isEmpty())) {
            log.info("At the candidate extraction stage: skipped saving results for transcript {} because its preparation was already completed.", transcriptId);
            return;
        }
        var memories = JdbcMemoryRepository.from(connection);
        int stored = 0;
        for (Memory memory : admitted) {
            if (transcript.eventPayload().get("supersedesMemoryId") == null
                    && memories.containsActiveText(memory.ownerScope(), memory.memoryText())) {
                log.info("At the candidate preparation stage: skipped duplicate memory text for transcript {}.", transcriptId);
                continue;
            }
            memories.store(memory);
            stored++;
            publisher.publish(embeddingTopic, memory.memoryId().toString(),
                    new MemoryReadyForEmbedding(memory.memoryId()));
        }
        log.info("At the candidate preparation stage: saved {} accepted memory candidate(s) for transcript {} and sent them to the embedding stage; awaiting transaction commit.",
                stored, transcriptId);
    }
}
