package com.example.okafkamemory.retrieval;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class MemorySearchRepository {
    private final JdbcClient jdbc;

    /** Uses the caller's connection without committing or closing it. */
    public static MemorySearchRepository from(Connection connection) {
        return new MemorySearchRepository(SingleConnectionJdbcClientFactory.create(connection));
    }

    public MemorySearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hit> find(String ownerScope, float[] queryVector, Set<String> queryTerms) {
        final VECTOR vector;
        try {
            vector = VECTOR.ofFloat32Values(queryVector);
        } catch (SQLException invalidVector) {
            throw new IllegalArgumentException("Invalid query embedding", invalidVector);
        }
        return jdbc.sql("""
                SELECT id, memory_text, created_at,
                       (2 - VECTOR_DISTANCE(embedding, ?, COSINE)) / 2 AS vector_score
                FROM event_memories
                WHERE owner_scope = ? AND status = 'active'
                  AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP)
                  AND embedding IS NOT NULL
                  AND VECTOR_DIMENSION_COUNT(embedding) = ?
                """)
                .param(1, vector, OracleType.VECTOR.getVendorTypeNumber())
                .param(2, ownerScope)
                .param(3, queryVector.length)
                .query((rs, row) -> new Hit(
                        UuidBytes.decode(rs.getBytes("id")), rs.getString("memory_text"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getDouble("vector_score"), MemorySearchTerms.score(rs.getString("memory_text"), queryTerms)))
                .list();
    }

    public record Hit(UUID memoryId, String memoryText, OffsetDateTime createdAt,
                      double vectorScore, double textScore) {
    }
}
