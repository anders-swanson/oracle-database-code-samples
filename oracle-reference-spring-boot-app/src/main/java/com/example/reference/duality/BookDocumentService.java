package com.example.reference.duality;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BookDocumentService {
    public static final String DOCUMENTS_SQL = """
            select dv.data."_id".number() as book_id,
                   json_serialize(dv.data returning clob) as document
            from book_documents_dv dv
            """;

    private final JdbcClient jdbc;

    public BookDocumentService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> find(long bookId) {
        return jdbc.sql(DOCUMENTS_SQL + " where dv.data.\"_id\".number() = :bookId")
                .param("bookId", bookId).query((rs, row) -> rs.getString("document")).optional();
    }
}
