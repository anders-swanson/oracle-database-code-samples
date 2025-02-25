package com.example.news.events.parser;

import java.util.ArrayList;
import java.util.List;

import com.example.news.model.News;
import com.example.news.genai.embeddingmodel.EmbeddingService;
import com.example.news.model.NewsVector;
import lombok.extern.slf4j.Slf4j;
import oracle.sql.VECTOR;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Parser {
    private final EmbeddingService embeddingService;
    private final Splitter splitter;

    public Parser(EmbeddingService embeddingService,
                  Splitter splitter) {
        this.embeddingService = embeddingService;
        this.splitter = splitter;
    }

    @Async
    public News parseAsync(String text) {
        News parsed = new News();
        List<NewsVector> embeddings = new ArrayList<>();
        List<String> chunks = splitter.split(text);
        List<VECTOR> vectors = embeddingService.embedAll(chunks);

        for (int i = 0; i < vectors.size(); i++) {
            NewsVector newsVector = new NewsVector();
            newsVector.setEmbedding(vectors.get(i));
            newsVector.setChunk(chunks.get(i));
            embeddings.add(newsVector);
        }

        parsed.setEmbeddings(embeddings);
        return parsed;
    }
}
