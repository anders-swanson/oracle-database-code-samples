package com.example.memory.model;

import java.util.List;

public final class MemoryDocument {
    private String memoryKind;
    private String title;
    private Memory memory = new Memory();

    public MemoryDocument() {
    }

    public static MemoryDocument create(
            String memoryKind,
            String title,
            String summary,
            String body,
            String service,
            String environment,
            String severity,
            String incidentId,
            String changeTicket,
            List<String> tags,
            String timestamp,
            String source
    ) {
        MemoryDocument document = new MemoryDocument();
        document.setMemoryKind(memoryKind);
        document.setTitle(title);

        Memory nested = new Memory();
        nested.setSummary(summary);
        nested.setBody(body);
        nested.setService(service);
        nested.setEnvironment(environment);
        nested.setSeverity(severity);
        nested.setIncidentId(incidentId);
        nested.setChangeTicket(changeTicket);
        nested.setTags(tags);
        nested.setTimestamp(timestamp);
        nested.setSource(source);
        document.setMemoryDoc(nested);
        return document;
    }

    public String searchableText() {
        return title + "\n" + summary() + "\n" + body() + "\n" + String.join(" ", tags());
    }

    public String summary() {
        return memoryDoc().getSummary();
    }

    public String body() {
        return memoryDoc().getBody();
    }

    public String service() {
        return memoryDoc().getService();
    }

    public String environment() {
        return memoryDoc().getEnvironment();
    }

    public String severity() {
        return memoryDoc().getSeverity();
    }

    public String incidentId() {
        return memoryDoc().getIncidentId();
    }

    public String changeTicket() {
        return memoryDoc().getChangeTicket();
    }

    public List<String> tags() {
        return List.copyOf(memoryDoc().getTags());
    }

    public String timestamp() {
        return memoryDoc().getTimestamp();
    }

    public String source() {
        return memoryDoc().getSource();
    }

    private Memory memoryDoc() {
        return memory == null ? new Memory() : memory;
    }

    public String getMemoryKind() {
        return memoryKind;
    }

    public void setMemoryKind(String memoryKind) {
        this.memoryKind = memoryKind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Memory getMemoryDoc() {
        return memory;
    }

    public void setMemoryDoc(Memory memory) {
        this.memory = memory == null ? new Memory() : memory;
    }
}
