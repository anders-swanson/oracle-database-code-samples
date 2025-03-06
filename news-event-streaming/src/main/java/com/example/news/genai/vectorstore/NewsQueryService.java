package com.example.news.genai.vectorstore;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.example.news.genai.embedding.EmbeddingService;
import com.example.news.model.SearchRequest;
import javax.sql.DataSource;
import oracle.sql.VECTOR;
import org.springframework.stereotype.Component;

@Component
public class NewsQueryService {
    private final NewsStore newsStore;
    private final EmbeddingService embeddingService;
    private final DataSource dataSource;

    public NewsQueryService(NewsStore newsStore, EmbeddingService embeddingService, DataSource dataSource) {
        this.newsStore = newsStore;
        this.embeddingService = embeddingService;
        this.dataSource = dataSource;
    }

    public List<String> search(SearchRequest searchRequest) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            VECTOR vector = embeddingService.embed(searchRequest.input());

            return newsStore.search(
                    searchRequest,
                    vector,
                    conn
            );
        }
    }

    public void cleanup() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            newsStore.cleanup(conn);
        }
    }
}
