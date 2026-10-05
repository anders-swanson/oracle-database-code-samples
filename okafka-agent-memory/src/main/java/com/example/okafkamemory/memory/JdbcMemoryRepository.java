package com.example.okafkamemory.memory;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.candidate.CandidateWork;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

public class JdbcMemoryRepository {
    private final JdbcClient jdbc;

    public JdbcMemoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Called in the curation transaction. The text is inserted only once. */
    public void promote(CandidateWork candidate, MemoryId memoryId) {
        jdbc.sql("""
                INSERT INTO event_memories (id, candidate_id, owner_scope, memory_text)
                VALUES (?, ?, ?, ?)
                """)
                .param(1, memoryId.toBytes())
                .param(2, UuidBytes.encode(candidate.candidateId()))
                .param(3, candidate.ownerScope())
                .param(4, candidate.candidateText(), Types.CLOB)
                .update();
        int changed = jdbc.sql("""
                UPDATE candidate_work SET status = 'PENDING_EMBEDDING'
                WHERE candidate_id = ? AND status = 'READY'
                """).param(UuidBytes.encode(candidate.candidateId())).update();
        if (changed != 1) {
            throw new IllegalStateException("Candidate changed while promoting " + candidate.candidateId());
        }
    }

    public Optional<String> pendingText(UUID candidateId) {
        return jdbc.sql("""
                SELECT m.memory_text FROM event_memories m
                JOIN candidate_work c ON c.candidate_id = m.candidate_id
                WHERE c.candidate_id = ? AND c.status = 'PENDING_EMBEDDING'
                """).param(UuidBytes.encode(candidateId)).query(String.class).optional();
    }

    public void completeEmbedding(UUID candidateId, float[] embedding) {
        final VECTOR vector;
        try {
            vector = VECTOR.ofFloat32Values(embedding);
        } catch (SQLException invalidVector) {
            throw new IllegalArgumentException("Invalid embedding", invalidVector);
        }
        int changed = jdbc.sql("""
                UPDATE event_memories SET embedding = ?
                WHERE candidate_id = ? AND embedding IS NULL
                  AND EXISTS (SELECT 1 FROM candidate_work c
                              WHERE c.candidate_id = event_memories.candidate_id
                                AND c.status = 'PENDING_EMBEDDING')
                """)
                .param(1, vector, OracleType.VECTOR.getVendorTypeNumber())
                .param(2, UuidBytes.encode(candidateId))
                .update();
        if (changed == 1) {
            int completed = jdbc.sql("""
                    UPDATE candidate_work SET status = 'COMPLETE'
                    WHERE candidate_id = ? AND status = 'PENDING_EMBEDDING'
                    """).param(UuidBytes.encode(candidateId)).update();
            if (completed != 1) {
                throw new IllegalStateException("Candidate changed while embedding " + candidateId);
            }
        }
    }
}
