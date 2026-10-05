package com.example.okafkamemory;

import com.example.okafkamemory.candidate.CandidateExtractor;
import com.example.okafkamemory.candidate.SpringAiCandidateJudge;
import com.example.okafkamemory.transcript.Transcript;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Run only with the oci-smoke Maven profile and OCI_COMPARTMENT_ID. */
@SpringBootTest(properties = {
        "memory.background-processing.enabled=false",
        "spring.datasource.oracleucp.initial-pool-size=0",
        "spring.datasource.oracleucp.min-pool-size=0"
})
class OciGenAiSmokeIT {
    @Autowired CandidateExtractor extractor;
    @Autowired SpringAiCandidateJudge judge;
    @Autowired ChatModel chat;
    @Autowired EmbeddingModel embeddings;
    @Value("${spring.ai.oci.genai.embedding.dimensions}") int dimensions;

    @Test
    void callsLiveChatAndEmbeddingProviders() {
        assertThat(chat.call("Reply with the word ready")).isNotBlank();
        assertThat(embeddings.embed("Oracle AI Database memory sample"))
                .hasSize(dimensions);
    }

    @Test
    void extractsDurableFactsAsJson() {
        var transcript = new Transcript(1, UUID.randomUUID(), "user:demo",
                Map.of("message", "Remember my favorite color is amber"), OffsetDateTime.now());
        var facts = extractor.extract(transcript);
        assertThat(facts).anySatisfy(fact -> {
            assertThat(fact.candidateText()).containsIgnoringCase("amber");
            assertThat(judge.score(transcript, fact)).isBetween(0, 100);
        });
    }
}
