package com.example.okafkamemory.memory;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import com.example.okafkamemory.candidate.CandidateWork;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

public class JdbcMemoryRepository {
    private final JdbcClient jdbc;

    /** Uses the caller's connection without committing or closing it. */
    public static JdbcMemoryRepository from(Connection connection) {
        return new JdbcMemoryRepository(SingleConnectionJdbcClientFactory.create(connection));
    }

    public JdbcMemoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Stored in the preparation transaction alongside the admitted candidate. */
    public void store(CandidateWork candidate, MemoryId memoryId) {
        jdbc.sql("""
                INSERT INTO event_memories (id, candidate_id, owner_scope, memory_text)
                VALUES (?, ?, ?, ?)
                """)
                .param(1, memoryId.toBytes())
                .param(2, UuidBytes.encode(candidate.candidateId()))
                .param(3, candidate.ownerScope())
                .param(4, candidate.candidateText(), Types.CLOB)
                .update();
    }

    public Optional<String> findText(UUID candidateId) {
        return jdbc.sql("SELECT memory_text FROM event_memories WHERE candidate_id = ?")
                .param(UuidBytes.encode(candidateId)).query(String.class).optional();
    }

    public void updateEmbedding(UUID candidateId, float[] embedding) {
        final VECTOR vector;
        try {
            vector = VECTOR.ofFloat32Values(embedding);
        } catch (SQLException invalidVector) {
            throw new IllegalArgumentException("Invalid embedding", invalidVector);
        }
        jdbc.sql("UPDATE event_memories SET embedding = ? WHERE candidate_id = ?")
                .param(1, vector, OracleType.VECTOR.getVendorTypeNumber())
                .param(2, UuidBytes.encode(candidateId))
                .update();
    }
}
