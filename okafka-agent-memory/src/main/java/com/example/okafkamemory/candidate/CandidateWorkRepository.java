package com.example.okafkamemory.candidate;

import com.example.okafkamemory.UuidBytes;
import com.example.okafkamemory.persistence.SingleConnectionJdbcClientFactory;
import com.example.okafkamemory.transcript.Transcript;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Connection;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CandidateWorkRepository {
    private static final String SELECT_CANDIDATE = """
            SELECT c.candidate_id, t.source_event_id, c.transcript_id, t.owner_scope,
                   c.evidence, c.candidate_text, c.judge_score
            FROM candidate_work c JOIN transcripts t ON t.transcript_id = c.transcript_id
            """;
    private static final RowMapper<CandidateWork> CANDIDATE = (rs, row) -> new CandidateWork(
            UuidBytes.decode(rs.getBytes("candidate_id")), UuidBytes.decode(rs.getBytes("source_event_id")),
            rs.getLong("transcript_id"), rs.getString("owner_scope"),
            rs.getString("evidence"), rs.getString("candidate_text"), rs.getInt("judge_score"));

    private final JdbcClient jdbc;

    /** Uses the caller's connection without committing or closing it. */
    public static CandidateWorkRepository from(Connection connection) {
        return new CandidateWorkRepository(SingleConnectionJdbcClientFactory.create(connection));
    }

    public CandidateWorkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CandidateWork> findCandidates(UUID sourceEventId) {
        return jdbc.sql(SELECT_CANDIDATE + " WHERE t.source_event_id = ? ORDER BY c.candidate_id")
                .param(UuidBytes.encode(sourceEventId)).query(CANDIDATE).list();
    }

    public Optional<CandidateWork> findReadyCandidate(UUID candidateId) {
        return jdbc.sql(SELECT_CANDIDATE + " WHERE c.candidate_id = ? AND c.status = 'READY'")
                .param(UuidBytes.encode(candidateId)).query(CANDIDATE).optional();
    }

    public CandidatePreparationResult complete(Transcript transcript, List<CandidateWork> candidates) {
        String outcome = candidates.isEmpty() ? "NO_MEMORY" : "DONE";
        int changed = jdbc.sql("""
                UPDATE transcripts SET preparation_status = ?
                WHERE transcript_id = ? AND preparation_status = 'READY'
                """).param(outcome).param(transcript.transcriptId()).update();
        if (changed == 0) {
            return findResult(transcript.sourceEventId()).orElseThrow();
        }
        for (CandidateWork candidate : candidates) {
            jdbc.sql("""
                    INSERT INTO candidate_work (candidate_id, transcript_id, evidence, candidate_text, judge_score)
                    VALUES (?, ?, ?, ?, ?)
                    """)
                    .param(1, UuidBytes.encode(candidate.candidateId()))
                    .param(2, transcript.transcriptId())
                    .param(3, candidate.evidence(), Types.CLOB)
                    .param(4, candidate.candidateText(), Types.CLOB)
                    .param(5, candidate.judgeScore())
                    .update();
        }
        return new CandidatePreparationResult(transcript.sourceEventId(),
                candidates.isEmpty() ? CandidatePreparationResult.Outcome.NO_MEMORY
                        : CandidatePreparationResult.Outcome.CANDIDATES, candidates);
    }

    public Optional<CandidatePreparationResult> findResult(UUID sourceEventId) {
        return jdbc.sql("""
                SELECT preparation_status FROM transcripts WHERE source_event_id = ?
                """).param(UuidBytes.encode(sourceEventId)).query(String.class).optional()
                .filter(status -> !status.equals("READY"))
                .map(status -> new CandidatePreparationResult(sourceEventId,
                        status.equals("NO_MEMORY") ? CandidatePreparationResult.Outcome.NO_MEMORY
                                : CandidatePreparationResult.Outcome.CANDIDATES,
                        findCandidates(sourceEventId)));
    }
}
