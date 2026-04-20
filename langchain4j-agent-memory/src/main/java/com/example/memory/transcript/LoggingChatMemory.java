package com.example.memory.transcript;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class LoggingChatMemory implements ChatMemory {
    private final ChatMemory delegate;
    private final String conversationId;
    private final ConversationTranscriptRepository transcriptSink;
    private final Executor executor;
    private final AtomicLong nextSequence = new AtomicLong(1L);
    private final AtomicInteger loggedMessageCount = new AtomicInteger(0);

    public LoggingChatMemory(
            ChatMemory delegate,
            String conversationId,
            ConversationTranscriptRepository transcriptSink,
            Executor executor
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId is required");
        this.transcriptSink = Objects.requireNonNull(transcriptSink, "transcriptSink is required");
        this.executor = Objects.requireNonNull(executor, "executor is required");
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
        appendAsync(message);
    }

    @Override
    public void set(Iterable<ChatMessage> messages) {
        List<ChatMessage> snapshot = toList(messages);
        delegate.set(snapshot);

        int alreadyLogged = loggedMessageCount.get();
        if (snapshot.size() <= alreadyLogged) {
            return;
        }
        for (int i = alreadyLogged; i < snapshot.size(); i++) {
            appendAsync(snapshot.get(i));
        }
    }

    @Override
    public List<ChatMessage> messages() {
        return delegate.messages();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private void appendAsync(ChatMessage message) {
        long sequence = nextSequence.getAndIncrement();
        loggedMessageCount.incrementAndGet();
        executor.execute(() -> persistMessage(sequence, message));
    }

    private void persistMessage(long sequence, ChatMessage message) {
        try {
            transcriptSink.store(TranscriptMessageMapper.toEntry(conversationId, sequence, message));
        } catch (RuntimeException e) {
            System.err.printf(
                    "Transcript logging failed for conversation %s seq %d: %s%n",
                    conversationId,
                    sequence,
                    e.getMessage()
            );
        }
    }

    private static List<ChatMessage> toList(Iterable<ChatMessage> messages) {
        if (messages instanceof List<ChatMessage> list) {
            return list;
        }
        List<ChatMessage> snapshot = new ArrayList<>();
        for (ChatMessage message : messages) {
            snapshot.add(message);
        }
        return snapshot;
    }
}
