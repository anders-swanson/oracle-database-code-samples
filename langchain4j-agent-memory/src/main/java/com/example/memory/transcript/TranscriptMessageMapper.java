package com.example.memory.transcript;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

public final class TranscriptMessageMapper {
    public static ConversationLogEntry toEntry(String conversationId, long messageSeq, ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return new ConversationLogEntry(
                    null,
                    conversationId,
                    messageSeq,
                    "SYSTEM",
                    message.type().name(),
                    systemMessage.text(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        if (message instanceof UserMessage userMessage) {
            JsonObject context = userContext(userMessage);
            return new ConversationLogEntry(
                    null,
                    conversationId,
                    messageSeq,
                    "USER",
                    message.type().name(),
                    primaryUserText(userMessage),
                    null,
                    null,
                    null,
                    toJsonString(context),
                    null
            );
        }
        if (message instanceof AiMessage aiMessage) {
            JsonObject context = aiContext(aiMessage);
            return new ConversationLogEntry(
                    null,
                    conversationId,
                    messageSeq,
                    "ASSISTANT",
                    message.type().name(),
                    primaryAiText(aiMessage),
                    null,
                    null,
                    null,
                    toJsonString(context),
                    null
            );
        }
        if (message instanceof ToolExecutionResultMessage toolMessage) {
            JsonObject context = toolContext(toolMessage);
            return new ConversationLogEntry(
                    null,
                    conversationId,
                    messageSeq,
                    "TOOL",
                    message.type().name(),
                    toolMessage.text(),
                    toolMessage.toolName(),
                    toolMessage.id(),
                    toolMessage.isError(),
                    toJsonString(context),
                    null
            );
        }
        return new ConversationLogEntry(
                null,
                conversationId,
                messageSeq,
                message.type().name(),
                message.type().name(),
                message.toString(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static JsonObject userContext(UserMessage message) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        addAttributes(builder, message.attributes());
        if (!message.hasSingleText()) {
            builder.add("content_summary", "non-text-or-multi-part user message");
        }
        return builder.build();
    }

    private static JsonObject aiContext(AiMessage message) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        addAttributes(builder, message.attributes());
        if (message.thinking() != null && !message.thinking().isBlank()) {
            builder.add("thinking_summary", truncate(message.thinking(), 500));
        }
        if (message.hasToolExecutionRequests()) {
            JsonArrayBuilder requests = Json.createArrayBuilder();
            for (ToolExecutionRequest request : message.toolExecutionRequests()) {
                requests.add(Json.createObjectBuilder()
                        .add("id", request.id())
                        .add("name", request.name())
                        .add("arguments", request.arguments() == null ? "" : request.arguments()));
            }
            builder.add("tool_requests", requests);
        }
        return builder.build();
    }

    private static JsonObject toolContext(ToolExecutionResultMessage message) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        addAttributes(builder, message.attributes());
        if (!message.hasSingleText()) {
            builder.add("content_summary", "multi-part tool result");
        }
        return builder.build();
    }

    private static void addAttributes(JsonObjectBuilder builder, Map<String, Object> attributes) {
        if (attributes.isEmpty()) {
            return;
        }
        JsonObjectBuilder attributesBuilder = Json.createObjectBuilder();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            attributesBuilder.add(entry.getKey(), String.valueOf(entry.getValue()));
        }
        builder.add("attributes", attributesBuilder);
    }

    private static String primaryUserText(UserMessage message) {
        if (message.hasSingleText()) {
            return message.singleText();
        }
        return "[non-text user message]";
    }

    private static String primaryAiText(AiMessage message) {
        if (message.text() != null && !message.text().isBlank()) {
            return message.text();
        }
        if (message.hasToolExecutionRequests()) {
            List<ToolExecutionRequest> requests = message.toolExecutionRequests();
            if (requests.size() == 1) {
                return "[assistant tool request: " + requests.getFirst().name() + "]";
            }
            return "[assistant tool request x" + requests.size() + "]";
        }
        return "[assistant message]";
    }

    private static String toJsonString(JsonObject object) {
        if (object.isEmpty()) {
            return null;
        }
        StringWriter writer = new StringWriter();
        Json.createWriter(writer).writeObject(object);
        return writer.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
