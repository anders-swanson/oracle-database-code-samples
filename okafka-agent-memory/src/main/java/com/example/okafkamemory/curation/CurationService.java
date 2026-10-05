package com.example.okafkamemory.curation;

import com.example.okafkamemory.candidate.CandidateWork;
import com.example.okafkamemory.candidate.CandidateWorkRepository;
import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.events.MemoryReadyForEmbedding;
import com.example.okafkamemory.memory.JdbcMemoryRepository;
import com.example.okafkamemory.memory.MemoryId;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;

@Service
public class CurationService {

    public void curate(Connection connection, UUID candidateId, EventPublisher publisher,
                       String embeddingTopic) throws Exception {
        var candidates = CandidateWorkRepository.from(connection);
        var memories = JdbcMemoryRepository.from(connection);
        CandidateWork candidate = candidates.findReadyCandidate(candidateId).orElse(null);
        if (candidate == null) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("okafka-agent-memory:memory:" + candidateId)
                .getBytes(StandardCharsets.UTF_8));
        memories.promote(candidate, new MemoryId(id));
        publisher.publish(embeddingTopic, candidateId.toString(), new MemoryReadyForEmbedding(candidateId));
    }
}
