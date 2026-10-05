package com.example.okafkamemory.candidate;

import com.example.okafkamemory.transcript.Transcript;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiCandidateExtractor implements CandidateExtractor {
    private static final String INSTRUCTIONS = """
            Extract durable, useful facts explicitly stated by the user in this transcript.
            Ignore greetings, transient requests, assistant claims, and secrets.
            Return only a JSON array of objects with string fields evidence and candidateText.
            Copy evidence exactly from the transcript. Return [] when there is no memory.
            """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<List<ExtractedCandidate>> outputConverter =
            new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});

    public SpringAiCandidateExtractor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public List<ExtractedCandidate> extract(Transcript transcript) {
        String response = chatModel.call(new Prompt(
                new SystemMessage(INSTRUCTIONS + outputConverter.getFormat()), new UserMessage(transcript.eventPayload())))
                .getResult().getOutput().getText();
        try {
            return outputConverter.convert(response);
        } catch (IllegalArgumentException invalidOutput) {
            throw new IllegalStateException("Candidate model returned invalid JSON", invalidOutput);
        }
    }
}
