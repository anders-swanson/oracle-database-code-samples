package com.example.news;


import javax.sql.DataSource;
import javax.xml.crypto.Data;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import com.example.news.events.RawNewsProducer;
import com.example.news.genai.chat.ChatService;
import com.example.news.genai.embeddingmodel.EmbeddingService;
import com.example.news.genai.splitter.LineSplitter;
import com.example.news.genai.vectorstore.NewsVectorStore;
import com.example.news.genai.workflow.ChatWorkflow;
import com.example.news.genai.workflow.EmbeddingWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class NewsService {
    private final ChatService chatService;
    private final EmbeddingService embeddingModel;
    private final NewsVectorStore vectorStore;
    private final RawNewsProducer rawNewsProducer;
    private final DataSource dataSource;

    private final String promptTemplate = """
            You are an assistant for question-answering tasks.
            Use the following pieces of retrieved context to answer the question.
            If you don't know the answer, just say that you don't know.
            Use three sentences maximum and keep the answer concise without extra detail.
            Question: {%s}
            Context: {%s}
            Answer:
            """;

    public NewsService(ChatService chatService,
                       EmbeddingService embeddingModel,
                       NewsVectorStore vectorStore, RawNewsProducer rawNewsProducer,
                       DataSource dataSource) {
        this.chatService = chatService;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.rawNewsProducer = rawNewsProducer;
        this.dataSource = dataSource;
    }

    @PostMapping("/news")
    public ResponseEntity<?> postNews(@RequestBody List<String> news) throws SQLException {
        rawNewsProducer.send(news);
        return ResponseEntity.created(location()).build();
    }

    @GetMapping("/news")
    public ResponseEntity<ChatResponse> getNews(@RequestBody ChatRequest chatRequest) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ChatWorkflow workflow = ChatWorkflow.builder()
                    .chatService(chatService)
                    .vectorStore(vectorStore)
                    .embeddingModel(embeddingModel)
                    .userQuestion(chatRequest.input)
                    .minScore(Objects.requireNonNullElse(chatRequest.minScore, 0.5))
                    .promptTemplate(promptTemplate)
                    .connection(conn)
                    .build();

            String response = workflow.call();
            return ResponseEntity.created(location()).body(new ChatResponse(response));
        }
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
