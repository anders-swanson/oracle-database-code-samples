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
}
