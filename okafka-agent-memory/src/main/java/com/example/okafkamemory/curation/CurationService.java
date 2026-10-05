package com.example.okafkamemory.curation;

import com.example.okafkamemory.candidate.CandidateWork;
import com.example.okafkamemory.candidate.CandidateWorkRepository;
import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.events.MemoryReadyForEmbedding;
import com.example.okafkamemory.memory.JdbcMemoryRepository;
import com.example.okafkamemory.memory.MemoryId;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Locale;
import java.util.UUID;

@Service
public class CurationService {
    private final SingleConnectionJdbcClientFactory clients;

    public CurationService(SingleConnectionJdbcClientFactory clients) {
        this.clients = clients;
    }

    public void curate(Connection connection, UUID candidateId, EventPublisher publisher,
                       String embeddingTopic) throws Exception {
        var jdbc = clients.create(connection);
        var candidates = new CandidateWorkRepository(jdbc);
        var memories = new JdbcMemoryRepository(jdbc);
        CandidateWork candidate = candidates.findReadyCandidate(candidateId).orElse(null);
        if (candidate == null) {
            return;
        }
        String text = candidate.candidateText().trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("reject ")) {
            candidates.markDecision(candidateId, "REJECTED");
        } else if (text.startsWith("maybe ")) {
            candidates.markDecision(candidateId, "REVIEW");
        } else {
            UUID id = UUID.nameUUIDFromBytes(("okafka-agent-memory:memory:" + candidateId)
                    .getBytes(StandardCharsets.UTF_8));
            memories.promote(candidate, new MemoryId(id));
            publisher.publish(embeddingTopic, candidateId.toString(), new MemoryReadyForEmbedding(candidateId));
        }
    }
}
