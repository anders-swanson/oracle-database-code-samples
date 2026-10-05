package com.example.okafkamemory.memory;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
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

    public void store(Memory memory) {
        jdbc.sql("""
                INSERT INTO event_memories (id, transcript_id, owner_scope, evidence, memory_text, judge_score)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .param(1, UuidBytes.encode(memory.memoryId()))
                .param(2, memory.transcriptId())
                .param(3, memory.ownerScope())
                .param(4, memory.evidence(), Types.CLOB)
                .param(5, memory.memoryText(), Types.CLOB)
                .param(6, memory.judgeScore())
                .update();
    }

    /** Exact text deduplication within the single-partition preparation stage. */
    public boolean containsActiveText(String ownerScope, String text) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM event_memories
                WHERE owner_scope = ? AND status = 'active'
                  AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP)
                  AND DBMS_LOB.COMPARE(memory_text, ?) = 0
                """).param(1, ownerScope).param(2, text, Types.CLOB)
                .query(Integer.class).single() > 0;
    }

    public boolean isActiveOwner(UUID memoryId, String ownerScope) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM event_memories
                WHERE id = ? AND owner_scope = ? AND status = 'active'
                  AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP)
                """).param(1, UuidBytes.encode(memoryId)).param(2, ownerScope)
                .query(Integer.class).single() == 1;
    }

    /** Called after embedding: replacement and deactivation commit together. */
    public void supersedeFor(UUID replacementId) {
        var correction = jdbc.sql("""
                SELECT m.owner_scope, JSON_VALUE(t.event_payload, '$.supersedesMemoryId') AS target_id
                FROM event_memories m JOIN transcripts t ON t.transcript_id = m.transcript_id
                WHERE m.id = ? AND m.status = 'active' AND m.embedding IS NOT NULL
                  AND (m.expires_at IS NULL OR m.expires_at > SYSTIMESTAMP)
                """).param(UuidBytes.encode(replacementId))
                .query((rs, row) -> new Correction(rs.getString("owner_scope"), rs.getString("target_id")))
                .optional();
        correction.filter(value -> value.targetId() != null).ifPresent(value ->
                jdbc.sql("UPDATE event_memories SET status = 'inactive' WHERE id = ? AND owner_scope = ? AND id <> ?")
                        .param(1, UuidBytes.encode(UUID.fromString(value.targetId())))
                        .param(2, value.ownerScope())
                        .param(3, UuidBytes.encode(replacementId)).update());
    }

    public List<Memory> findByTranscript(long transcriptId) {
        return jdbc.sql("""
                SELECT id, transcript_id, owner_scope, evidence, memory_text, judge_score
                FROM event_memories WHERE transcript_id = ? ORDER BY id
                """).param(transcriptId).query((rs, row) -> new Memory(
                        UuidBytes.decode(rs.getBytes("id")), rs.getLong("transcript_id"),
                        rs.getString("owner_scope"), rs.getString("evidence"),
                        rs.getString("memory_text"), rs.getInt("judge_score"))).list();
    }

    public Optional<String> findText(UUID memoryId) {
        return jdbc.sql("SELECT memory_text FROM event_memories WHERE id = ?")
                .param(UuidBytes.encode(memoryId)).query(String.class).optional();
    }

    public void updateEmbedding(UUID memoryId, float[] embedding) {
        final VECTOR vector;
        try {
            vector = VECTOR.ofFloat32Values(embedding);
        } catch (SQLException invalidVector) {
            throw new IllegalArgumentException("Invalid embedding", invalidVector);
        }
        jdbc.sql("UPDATE event_memories SET embedding = ? WHERE id = ?")
                .param(1, vector, OracleType.VECTOR.getVendorTypeNumber())
                .param(2, UuidBytes.encode(memoryId))
                .update();
    }

    private record Correction(String ownerScope, String targetId) {
    }
}
