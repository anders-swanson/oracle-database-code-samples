package com.example.okafkamemory.candidate;

import com.example.okafkamemory.events.CandidateReady;
import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.Transcript;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CandidatePreparationService {
    private static final Pattern SENSITIVE_CONTENT = Pattern.compile(
            "(?i)\\b(password|api[ -]?key|access token|private key|social security number)\\b");
    private final SingleConnectionJdbcClientFactory clients;
    private final CandidateExtractor extractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CandidatePreparationService(SingleConnectionJdbcClientFactory clients, CandidateExtractor extractor) {
        this.clients = clients;
        this.extractor = extractor;
    }

    public void process(Connection connection, long transcriptId, EventPublisher publisher,
                        String candidateTopic) throws Exception {
        var jdbc = clients.create(connection);
        var transcripts = new JdbcTranscriptRepository(jdbc);
        var workRepository = new CandidateWorkRepository(jdbc);
        Optional<Transcript> ready = transcripts.findReady(transcriptId);
        if (ready.isEmpty()) return;
        Transcript transcript = ready.orElseThrow();

        JsonNode payload;
        try {
            payload = objectMapper.readTree(transcript.eventPayload());
        } catch (JsonProcessingException invalidTranscript) {
            throw new IllegalStateException("Stored transcript is not JSON", invalidTranscript);
        }
        var unique = new LinkedHashMap<UUID, CandidateWork>();
        for (ExtractedCandidate extracted : extractor.extract(transcript)) {
            if (extracted == null || extracted.evidence() == null || extracted.candidateText() == null) {
                continue;
            }
            String evidence = extracted.evidence().trim();
            String text = extracted.candidateText().trim();
            if (evidence.isEmpty() || text.isEmpty() || !containsEvidence(payload, evidence)
                    || SENSITIVE_CONTENT.matcher(evidence).find()
                    || SENSITIVE_CONTENT.matcher(text).find()) {
                continue;
            }
            UUID id = UUID.nameUUIDFromBytes(("okafka-agent-memory:candidate:\u0000"
                    + transcript.sourceEventId() + "\u0000" + text).getBytes(StandardCharsets.UTF_8));
            unique.putIfAbsent(id, new CandidateWork(id, transcript.sourceEventId(),
                    transcript.transcriptId(), transcript.ownerScope(), evidence, text));
        }
        var result = workRepository.complete(transcript, List.copyOf(unique.values()));
        for (CandidateWork candidate : result.candidates()) {
            publisher.publish(candidateTopic, candidate.candidateId().toString(),
                    new CandidateReady(candidate.candidateId()));
        }
    }

    private static boolean containsEvidence(JsonNode node, String evidence) {
        if (node.isTextual()) {
            return node.asText().contains(evidence);
        }
        for (JsonNode child : node) {
            if (containsEvidence(child, evidence)) {
                return true;
            }
        }
        return false;
    }
}
