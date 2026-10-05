package com.example.okafkamemory.retrieval;

import com.example.okafkamemory.UuidBytes;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
public class MemorySearchRepository {
    private final JdbcClient jdbc;

    public MemorySearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hit> find(String ownerScope, float[] queryVector, String query) {
        final VECTOR vector;
        try {
            vector = VECTOR.ofFloat32Values(queryVector);
        } catch (SQLException invalidVector) {
            throw new IllegalArgumentException("Invalid query embedding", invalidVector);
        }
        List<String> terms = Arrays.stream(query.split("[^a-zA-Z0-9]+"))
                .filter(term -> !term.isBlank()).distinct().toList();
        String termPattern = terms.isEmpty() ? "a^" : String.join("|", terms);
        return jdbc.sql("""
                SELECT id, memory_text, created_at,
                       (2 - VECTOR_DISTANCE(embedding, ?, COSINE)) / 2 AS vector_score,
                       CASE WHEN INSTR(LOWER(memory_text), LOWER(?)) > 0 THEN 1
                            ELSE LEAST(1, REGEXP_COUNT(memory_text, ?, 1, 'i') / ?)
                       END AS text_score
                FROM event_memories
                WHERE owner_scope = ? AND status = 'active'
                  AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP)
                  AND embedding IS NOT NULL
                  AND VECTOR_DIMENSION_COUNT(embedding) = ?
                """)
                .param(1, vector, OracleType.VECTOR.getVendorTypeNumber())
                .param(2, query)
                .param(3, termPattern)
                .param(4, Math.max(1, terms.size()))
                .param(5, ownerScope)
                .param(6, queryVector.length)
                .query((rs, row) -> new Hit(
                        UuidBytes.decode(rs.getBytes("id")), rs.getString("memory_text"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getDouble("vector_score"), rs.getDouble("text_score")))
                .list();
    }

    public record Hit(UUID memoryId, String memoryText, OffsetDateTime createdAt,
                      double vectorScore, double textScore) {
    }
}
