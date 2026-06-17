package com.example.memory.transcript;

import oracle.jdbc.datasource.impl.OracleDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Disabled
class ConversationTranscriptRepositoryIntegrationTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.2-full-faststart")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withUsername("testuser")
            .withPassword("testpwd");

    private ConversationTranscriptRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(oracle.getJdbcUrl());
        dataSource.setUser(oracle.getUsername());
        dataSource.setPassword(oracle.getPassword());
        repository = new ConversationTranscriptRepository(dataSource);
        repository.initializeSchema();
    }

    @Test
    void storesAndReadsTranscriptRowsByConversation() {
        repository.store(new ConversationLogEntry(
                null,
                "session-42",
                1L,
                "USER",
                "USER",
                "How did checkout fail?",
                null,
                null,
                null,
                "{\"source\":\"integration-test\"}",
                null
        ));
        repository.store(new ConversationLogEntry(
                null,
                "session-42",
                2L,
                "TOOL",
                "TOOL_EXECUTION_RESULT",
                "Found incident INC4721",
                "searchMemories",
                "tool-call-2",
                false,
                "{\"matched_by\":\"BOTH\"}",
                null
        ));

        List<ConversationLogEntry> rows = repository.findByConversationId("session-42");

        assertEquals(2, rows.size());
        assertEquals(List.of(1L, 2L), rows.stream().map(ConversationLogEntry::messageSeq).toList());
        assertEquals("USER", rows.get(0).role());
        assertEquals("TOOL", rows.get(1).role());
        assertEquals("searchMemories", rows.get(1).toolName());
        assertEquals("tool-call-2", rows.get(1).toolCallId());
        assertEquals(Boolean.FALSE, rows.get(1).error());
        assertTrue(rows.get(1).contextJson().contains("matched_by"));
        assertNotNull(rows.get(0).createdAt());
        assertNotNull(rows.get(1).createdAt());
    }
}
