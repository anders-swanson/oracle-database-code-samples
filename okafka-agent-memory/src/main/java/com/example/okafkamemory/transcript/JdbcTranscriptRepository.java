package com.example.okafkamemory.transcript;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import com.oracle.spring.json.jsonb.JSONB;
import jakarta.json.stream.JsonParser;
import oracle.jdbc.OracleTypes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JdbcTranscriptRepository implements TranscriptRepository {
    private static final JSONB JSON = JSONB.createDefault();
    private static final String SELECT_COLUMNS = """
            SELECT transcript_id, source_event_id, owner_scope, event_payload, created_at
            FROM transcripts
            """;

    @SuppressWarnings("unchecked") // JSONB accepts Class<Map>; payload keys are JSON object field names.
    private static final RowMapper<Transcript> TRANSCRIPT_ROW_MAPPER = (resultSet, rowNumber) -> {
        try (var parser = resultSet.getObject("event_payload", JsonParser.class)) {
            return new Transcript(
                    resultSet.getLong("transcript_id"),
                    UuidBytes.decode(resultSet.getBytes("source_event_id")),
                    resultSet.getString("owner_scope"),
                    JSON.fromOSON(parser, Map.class),
                    resultSet.getObject("created_at", OffsetDateTime.class));
        }
    };

    private final JdbcClient jdbcClient;

    /** Uses the caller's connection without committing or closing it. */
    public static JdbcTranscriptRepository from(Connection connection) {
        return new JdbcTranscriptRepository(SingleConnectionJdbcClientFactory.create(connection));
    }

    public JdbcTranscriptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Transcript> insertIfAbsent(TranscriptDraft draft) {
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcClient.sql("""
                            INSERT INTO transcripts (source_event_id, owner_scope, event_payload)
                            VALUES (?, ?, ?)
                            """)
                    .param(1, UuidBytes.encode(draft.sourceEventId()))
                    .param(2, draft.ownerScope())
                    .param(3, JSON.toOSON(draft.eventPayload()), OracleTypes.JSON)
                    .update(keyHolder, "TRANSCRIPT_ID");
        } catch (DuplicateKeyException duplicateSourceEvent) {
            return Optional.empty();
        }

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Oracle did not return the generated transcript ID");
        }
        return Optional.of(findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException(
                        "Inserted transcript could not be read back: " + generatedId)));
    }

    @Override
    public Optional<Transcript> findById(long transcriptId) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE transcript_id = ?")
                .param(transcriptId)
                .query(TRANSCRIPT_ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Transcript> findBySourceEventId(UUID sourceEventId) {
        return jdbcClient.sql(SELECT_COLUMNS + " WHERE source_event_id = ?")
                .param(UuidBytes.encode(sourceEventId))
                .query(TRANSCRIPT_ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Transcript> findReady(long transcriptId) {
        return jdbcClient.sql(SELECT_COLUMNS + """
                 WHERE transcript_id = ? AND preparation_status = 'READY'
                 """)
                .param(transcriptId).query(TRANSCRIPT_ROW_MAPPER).optional();
    }

}
