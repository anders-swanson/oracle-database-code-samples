package com.example.reference.vector;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DemoEmbeddings implements CommandLineRunner {
    private final JdbcClient jdbc;
    private final BookEmbeddingService embeddings;

    public DemoEmbeddings(JdbcClient jdbc, BookEmbeddingService embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
    }

    record Synopsis(long bookId, String text) { }

    @Override
    public void run(String... args) {
        var synopses = jdbc.sql("select book_id, synopsis from book_embeddings where embedding is null")
                .query((rs, row) -> new Synopsis(rs.getLong("book_id"), rs.getString("synopsis"))).list();
        for (var synopsis : synopses) {
            embeddings.index(synopsis.bookId(), synopsis.text());
        }
    }
}
