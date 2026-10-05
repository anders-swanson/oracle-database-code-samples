package com.example.okafkamemory.intake;

import com.example.okafkamemory.events.EventPublisher;
import com.example.okafkamemory.events.TranscriptReady;
import com.example.okafkamemory.transcript.JdbcTranscriptRepository;
import com.example.okafkamemory.transcript.TranscriptDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;

@Service
public class IntakeService {
    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);
    private final IntakePolicy policy;

    public IntakeService(IntakePolicy policy) {
        this.policy = policy;
    }

    /** Writes on the caller's transaction; the OKafka consumer owns commit and rollback. */
    public void process(Connection connection, IncomingEvent event,
                        EventPublisher publisher, String transcriptTopic) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException("incoming event is required");
        }
        String reason = policy.rejectionReason(event);
        if (reason != null) {
            log.warn("At the intake stage: rejected incoming event {} because {}.", event.sourceEventId(), reason);
            return;
        }
        if (connection.getAutoCommit()) {
            throw new IllegalArgumentException("Intake requires a connection with auto-commit disabled");
        }
        var transcripts = JdbcTranscriptRepository.from(connection);
        var inserted = transcripts.insertIfAbsent(new TranscriptDraft(
                event.sourceEventId(), event.ownerScope(), event.transcriptPayload()));
        if (inserted.isEmpty()) {
            log.info("At the intake stage: skipped incoming event {} because a transcript already exists for it.", event.sourceEventId());
            return;
        }
        var transcript = inserted.orElseThrow();
        log.info("At the intake stage: created transcript {} from incoming event {}; awaiting transaction commit.",
                transcript.transcriptId(), event.sourceEventId());
        publisher.publish(transcriptTopic, Long.toString(transcript.transcriptId()),
                new TranscriptReady(transcript.transcriptId()));
    }
}
