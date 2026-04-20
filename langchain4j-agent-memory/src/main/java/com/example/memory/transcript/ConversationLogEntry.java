package com.example.memory.transcript;

import java.time.OffsetDateTime;

public record ConversationLogEntry(
        Long id,
        String conversationId,
        long messageSeq,
        String role,
        String messageType,
        String messageText,
        String toolName,
        String toolCallId,
        Boolean error,
        String contextJson,
        OffsetDateTime createdAt
) {
}
