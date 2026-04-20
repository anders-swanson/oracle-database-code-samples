package com.example.memory.agent;

import com.example.memory.model.MemoryDocument;
import com.example.memory.model.MemoryHit;
import com.example.memory.search.MemoryRepository;
import com.example.memory.search.MemorySearchRequest;
import dev.langchain4j.agent.tool.Tool;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MemoryTools {
    private final MemoryRepository repository;

    public MemoryTools(MemoryRepository repository) {
        this.repository = repository;
    }

    @Tool("Search Oracle AI Database memory for prior incidents, runbooks, handoffs, or change history when it would help answer the user.")
    public String searchMemories(String question) {
        logToolEvent("searchMemories", "start", "question=\"" + summarize(question) + "\"");
        List<MemoryHit> hits = repository.combinedSearch(new MemorySearchRequest(question, 5));
        if (hits.isEmpty()) {
            logToolEvent("searchMemories", "done", "hits=0");
            return "No close memory matches found.";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            MemoryHit hit = hits.get(i);
            builder.append(i + 1)
                    .append(". [")
                    .append(hit.reference())
                    .append("] ")
                    .append(hit.title())
                    .append(" (")
                    .append(hit.memoryKind().toLowerCase(Locale.US))
                    .append(", ")
                    .append(hit.service())
                    .append('/')
                    .append(hit.environment())
                    .append(')')
                    .append('\n')
                    .append("Summary: ")
                    .append(hit.summary())
                    .append('\n');
            if (hit.incidentId() != null && !hit.incidentId().isBlank()) {
                builder.append("Incident: ").append(hit.incidentId()).append('\n');
            }
            if (hit.changeTicket() != null && !hit.changeTicket().isBlank()) {
                builder.append("Change: ").append(hit.changeTicket()).append('\n');
            }
        }
        logToolEvent("searchMemories", "done", "hits=" + hits.size());
        return builder.toString().trim();
    }

    @Tool("Store a new durable memory when the conversation produced a useful handoff, decision, or operational fact worth keeping.")
    public String storeMemory(
            String kind,
            String title,
            String summary,
            String body,
            String service,
            String environment,
            String incidentId,
            String changeTicket,
            String tagsCsv
    ) {
        logToolEvent(
                "storeMemory",
                "start",
                "kind=" + normalizeOrDefault(kind, "EPISODIC")
                        + ", title=\"" + summarize(title) + "\""
        );
        MemoryDocument document = MemoryDocument.create(
                normalizeOrDefault(kind, "EPISODIC"),
                title,
                summary,
                body,
                normalizeOrDefault(service, "checkout"),
                normalizeOrDefault(environment, "prod"),
                "INFO",
                normalizeBlank(incidentId),
                normalizeBlank(changeTicket),
                parseTags(tagsCsv),
                Instant.now().toString(),
                "agent-writeback"
        );
        long id = repository.storeMemory(document);
        logToolEvent("storeMemory", "done", "id=M" + id + ", title=\"" + summarize(title) + "\"");
        return "Stored memory M" + id + " with title '" + title + "'.";
    }

    private static void logToolEvent(String toolName, String phase, String details) {
        System.out.println("\r[tool] " + toolName + " " + phase + " - " + details);
        System.out.flush();
    }

    private static String summarize(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return "";
        }
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private static List<String> parseTags(String tagsCsv) {
        return Arrays.stream(tagsCsv.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String normalizeOrDefault(String value, String fallback) {
        String normalized = normalizeBlank(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
