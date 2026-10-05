package com.example.okafkamemory.intake;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.events.TranscriptReady;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.Transcript;
import com.example.okafkamemory.transcript.TranscriptDraft;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;

@Service
public class IntakeService {
    private final SingleConnectionJdbcClientFactory jdbcClientFactory;
    private final IntakePolicy policy;

    public IntakeService(SingleConnectionJdbcClientFactory jdbcClientFactory,
                         IntakePolicy policy) {
        this.jdbcClientFactory = jdbcClientFactory;
        this.policy = policy;
    }

    /** Writes on the caller's transaction; the OKafka consumer owns commit and rollback. */
    public IntakeResult process(Connection connection, IncomingEvent event,
                                EventPublisher publisher, String transcriptTopic) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException("incoming event is required");
        }

        if (connection.getAutoCommit()) {
            throw new IllegalArgumentException("Intake requires a connection with auto-commit disabled");
        }
        JdbcClient jdbc = jdbcClientFactory.create(connection);
        var transcripts = new JdbcTranscriptRepository(jdbc);

        String reason = policy.rejectionReason(event);
        try {
            jdbc.sql("INSERT INTO intake_outcomes (source_event_id, outcome) VALUES (?, 'PENDING')")
                    .param(UuidBytes.encode(event.sourceEventId()))
                    .update();
        } catch (DuplicateKeyException duplicate) {
            return findOutcome(jdbc, event.sourceEventId()).orElseThrow(() -> duplicate);
        }

        if (reason != null) {
            jdbc.sql("""
                            UPDATE intake_outcomes SET outcome = 'FILTERED', reason = ?
                            WHERE source_event_id = ?
                            """)
                    .param(reason)
                    .param(UuidBytes.encode(event.sourceEventId()))
                    .update();
            return new IntakeResult(event.sourceEventId(), IntakeResult.Outcome.FILTERED, reason, null);
        }

        Transcript transcript = transcripts.storeIfAbsent(new TranscriptDraft(
                event.sourceEventId(), event.ownerScope(), event.transcriptPayload()));
        jdbc.sql("""
                        UPDATE intake_outcomes
                        SET outcome = 'ACCEPTED', transcript_id = ?
                        WHERE source_event_id = ?
                        """)
                .param(transcript.transcriptId())
                .param(UuidBytes.encode(event.sourceEventId()))
                .update();
        publisher.publish(transcriptTopic, Long.toString(transcript.transcriptId()),
                new TranscriptReady(transcript.transcriptId()));
        return new IntakeResult(event.sourceEventId(), IntakeResult.Outcome.ACCEPTED,
                null, transcript.transcriptId());
    }

    private Optional<IntakeResult> findOutcome(JdbcClient jdbc, UUID sourceEventId) {
        return jdbc.sql("""
                        SELECT source_event_id, outcome, reason, transcript_id
                        FROM intake_outcomes WHERE source_event_id = ?
                        """)
                .param(UuidBytes.encode(sourceEventId))
                .query((rs, rowNumber) -> new IntakeResult(
                        UuidBytes.decode(rs.getBytes("source_event_id")),
                        IntakeResult.Outcome.valueOf(rs.getString("outcome")),
                        rs.getString("reason"),
                        rs.getObject("transcript_id", Long.class)))
                .optional();
    }

}
