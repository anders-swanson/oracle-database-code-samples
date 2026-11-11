package com.example.reference.graph;

import com.example.reference.book.BookRepository;
import com.example.reference.book.CatalogItemNotFound;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookGraphService {
    // Also embedded in the application's converged SQL query; one source of graph traversal semantics.
    public static final String CANDIDATES_SQL = """
            select distinct candidate_id
            from graph_table (book_graph
                match (seed is book where seed.id = :seedId)
                    -[edge is related]->{1,2} (candidate is book)
                where seed.id <> candidate.id
                columns (candidate.id as candidate_id)
            )
            """;

    private final JdbcClient jdbc;
    private final BookRepository books;

    public BookGraphService(JdbcClient jdbc, BookRepository books) {
        this.jdbc = jdbc;
        this.books = books;
    }

    public List<Long> relatedBooks(long seedId, int limit) {
        return jdbc.sql(CANDIDATES_SQL + " order by candidate_id fetch first :limit rows only")
                .param("seedId", seedId).param("limit", limit).query(Long.class).list();
    }

    @Transactional
    public void relate(long sourceId, long targetId) {
        if (!books.existsById(sourceId)) {
            throw new CatalogItemNotFound("Book", sourceId);
        }
        if (!books.existsById(targetId)) {
            throw new CatalogItemNotFound("Book", targetId);
        }
        jdbc.sql("""
                merge into book_relations r
                using (select :sourceId source_id, :targetId target_id from dual) incoming
                on (r.source_id = incoming.source_id and r.target_id = incoming.target_id)
                when not matched then insert (source_id, target_id)
                values (incoming.source_id, incoming.target_id)
                """).param("sourceId", sourceId).param("targetId", targetId).update();
    }
}
