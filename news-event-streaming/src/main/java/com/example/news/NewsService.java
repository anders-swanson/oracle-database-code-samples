package com.example.news;


import javax.sql.DataSource;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;

import com.example.news.events.producerconsumer.RawNewsProducer;
import com.example.news.genai.chat.ChatService;
import com.example.news.genai.embeddingmodel.EmbeddingService;
import com.example.news.genai.vectorstore.NewsVectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class NewsService {
    private final RawNewsProducer rawNewsProducer;

    private final String promptTemplate = """
            You are an assistant for question-answering tasks.
            Use the following pieces of retrieved context to answer the question.
            If you don't know the answer, just say that you don't know.
            Use three sentences maximum and keep the answer concise without extra detail.
            Question: {%s}
            Context: {%s}
            Answer:
            """;

    public NewsService(RawNewsProducer rawNewsProducer) {
        this.rawNewsProducer = rawNewsProducer;
    }


    @PostMapping("/news")
    public ResponseEntity<?> postNews(@RequestBody List<String> news) throws SQLException {
        rawNewsProducer.send(news);
        return ResponseEntity.created(location()).build();
    }

    @GetMapping("/news")
    public ResponseEntity<ChatResponse> getNews(@RequestBody ChatRequest chatRequest) throws Exception {
        return ResponseEntity.created(location()).body(new ChatResponse(""));
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
