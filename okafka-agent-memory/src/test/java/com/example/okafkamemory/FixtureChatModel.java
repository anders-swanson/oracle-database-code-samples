package com.example.okafkamemory;

import com.example.okafkamemory.candidate.ExtractedCandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/** Test-only responses for deterministic persistence and curation checks. */
public class FixtureChatModel implements ChatModel {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            JsonNode transcript = objectMapper.readTree(prompt.getUserMessage().getText());
            if (prompt.getSystemMessage().getText().contains("memory candidate judge")) {
                String text = transcript.path("candidate").path("candidateText").asText();
                int score = text.contains("password") || text.startsWith("reject ") ? 10
                        : text.startsWith("maybe ") ? 40
                        : text.equals("threshold fact") ? 80
                        : text.equals("above threshold fact") ? 81
                        : text.equals("invalid judge output") ? 101 : 90;
                return response("{\"score\":" + score + "}");
            }
            List<ExtractedCandidate> candidates = new ArrayList<>();
            addCandidate(transcript.path("message"), candidates);
            for (JsonNode message : transcript.path("messages")) {
                if ("user".equalsIgnoreCase(message.path("role").asText())) {
                    addCandidate(message.path("text"), candidates);
                }
            }
            String json = objectMapper.writeValueAsString(candidates);
            return response(json);
        } catch (Exception invalidTranscript) {
            throw new IllegalArgumentException("Transcript must be JSON", invalidTranscript);
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static void addCandidate(JsonNode message, List<ExtractedCandidate> candidates) {
        if (!message.isTextual()) {
            return;
        }
        String evidence = message.asText().trim();
        if (evidence.regionMatches(true, 0, "remember ", 0, 9)) {
            String text = evidence.substring(9).trim();
            if (!text.isEmpty()) {
                candidates.add(new ExtractedCandidate(evidence, text));
            }
        }
    }
}
