package com.example;


import java.net.URI;
import java.util.List;
import java.util.Objects;

import com.example.chat.OCIChatService;
import com.example.embeddingmodel.OCIEmbeddingService;
import com.example.splitter.LineSplitter;
import com.example.vectorstore.OracleVectorStore;
import com.example.workflow.ChatWorkflow;
import com.example.workflow.SimpleEmbeddingWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class VectorStoreController {
    private final OCIChatService chatService;
    private final OCIEmbeddingService embeddingModel;
    private final OracleVectorStore vectorStore;

    private final String promptTemplate = """
            You are an assistant for question-answering tasks.
            Use the following pieces of retrieved context to answer the question.
            If you don't know the answer, just say that you don't know.
            Use three sentences maximum and keep the answer concise without extra detail.
            Question: {%s}
            Context: {%s}
            Answer:
            """;

    public VectorStoreController(OCIChatService chatService, OCIEmbeddingService embeddingModel, OracleVectorStore vectorStore) {
        this.chatService = chatService;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    @PostMapping("/embed")
    public ResponseEntity<?> embed(@RequestBody List<String> documents) {
        SimpleEmbeddingWorkflow workflow = SimpleEmbeddingWorkflow.builder()
                .documents(documents)
                .embeddingModel(embeddingModel)
                .vectorStore(vectorStore)
                .splitter(new LineSplitter())
                .build();

        Thread.startVirtualThread(workflow);
        return ResponseEntity.created(location()).build();
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest) throws Exception {
        ChatWorkflow workflow = ChatWorkflow.builder()
                .chatService(chatService)
                .vectorStore(vectorStore)
                .embeddingModel(embeddingModel)
                .userQuestion(chatRequest.input)
                .minScore(Objects.requireNonNullElse(chatRequest.minScore, 0.5))
                .promptTemplate(promptTemplate)
                .build();

        String response = workflow.call();
        return ResponseEntity.created(location()).body(new ChatResponse(response));
    }


    @PostMapping("embed/reset")
    public ResponseEntity<?> reset() {
        vectorStore.reset();
        return ResponseEntity.created(location()).build();
    }

    @GetMapping("/embed")
    public ResponseEntity<EmbeddingCount> countEmbeddings() {
        int count = vectorStore.countEmbeddings();
        return ResponseEntity.ok(new EmbeddingCount(count));
    }

    public record EmbeddingCount(int count) {
    }

    public record ChatResponse(String response) {
    }


    public record ChatRequest(String input,
                              Double minScore) {

    }


    private URI location() {
        return ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();
    }
}
