package com.example.okafkamemory.transcript;

import com.example.okafkamemory.UuidBytes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public class JdbcTranscriptRepository implements TranscriptRepository {
    private static final String SELECT_COLUMNS = """
            SELECT transcript_id, source_event_id, owner_scope, event_payload, created_at
            FROM transcripts
            """;

    private static final RowMapper<Transcript> TRANSCRIPT_ROW_MAPPER = (resultSet, rowNumber) ->
            new Transcript(
                    resultSet.getLong("transcript_id"),
                    UuidBytes.decode(resultSet.getBytes("source_event_id")),
                    resultSet.getString("owner_scope"),
                    resultSet.getString("event_payload"),
                    resultSet.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public JdbcTranscriptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Transcript storeIfAbsent(TranscriptDraft draft) {
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcClient.sql("""
                            INSERT INTO transcripts (source_event_id, owner_scope, event_payload)
                            VALUES (?, ?, ?)
                            """)
                    .param(1, UuidBytes.encode(draft.sourceEventId()))
                    .param(2, draft.ownerScope())
                    .param(3, draft.eventPayload(), Types.CLOB)
                    .update(keyHolder, "TRANSCRIPT_ID");
        } catch (DuplicateKeyException duplicateSourceEvent) {
            return findBySourceEventId(draft.sourceEventId())
                    .orElseThrow(() -> duplicateSourceEvent);
        }

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Oracle did not return the generated transcript ID");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException(
                        "Inserted transcript could not be read back: " + generatedId));
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
