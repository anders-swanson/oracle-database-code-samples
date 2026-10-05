package com.example.okafkamemory;

import com.example.okafkamemory.candidate.ExtractedCandidate;
import com.example.okafkamemory.candidate.SpringAiCandidateJudge;
import com.example.okafkamemory.transcript.Transcript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiCandidateJudgeTest {
    private final Transcript transcript = new Transcript(1, UUID.randomUUID(), "user:demo",
            Map.of("message", "My OKafka events use OSON"), OffsetDateTime.now());
    private final ExtractedCandidate candidate = new ExtractedCandidate("Uses OSON for OKafka events", "OKafka events use OSON serialization");
    private final StubChatModel model = new StubChatModel();
    private final SpringAiCandidateJudge judge = new SpringAiCandidateJudge(model);

    @ParameterizedTest
    @ValueSource(ints = {0, 71, 100})
    void acceptsScoresInRangeAndProvidesSourceAndCandidate(int score) {
        respond("{\"score\":" + score + "}");
        assertThat(judge.score(transcript, candidate)).isEqualTo(score);
        assertThat(model.lastPrompt.getUserMessage().getText())
                .contains("My OKafka events use OSON", "Uses OSON for OKafka events", "OKafka events use OSON serialization");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"score\":-1}", "{\"score\":101}", "{\"score\":70.5}"})
    void rejectsMissingOrInvalidScores(String output) {
        respond(output);
        assertThatThrownBy(() -> judge.score(transcript, candidate)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedModelOutput() {
        respond("not JSON");
        assertThatThrownBy(() -> judge.score(transcript, candidate))
                .isInstanceOf(IllegalStateException.class).hasMessage("Candidate judge returned invalid JSON");
    }

    private void respond(String output) {
        model.output = output;
    }

    private static class StubChatModel implements ChatModel {
        private String output;
        private Prompt lastPrompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(output))));
        }
    }
}
