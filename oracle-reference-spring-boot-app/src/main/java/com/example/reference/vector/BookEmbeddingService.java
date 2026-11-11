package com.example.reference.vector;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import oracle.jdbc.OracleTypes;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;

@Service
public class BookEmbeddingService {
    private final AllMiniLmL6V2EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public BookEmbeddingService(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Native VECTOR bind; the same model and dimensions are used for indexing and querying. */
    public SqlParameterValue embed(String text) {
        try {
            return new SqlParameterValue(OracleTypes.VECTOR,
                    VECTOR.ofFloat32Values(model.embed(text).content().vector()));
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to encode the embedding", failure);
        }
    }

    public void index(long bookId, String synopsis) {
        // Inference runs before starting the database transaction or borrowing its connection.
        SqlParameterValue vector = embed(synopsis);
        transactions.executeWithoutResult(status -> jdbc.sql("""
                merge into book_embeddings target
                using (select :bookId book_id from dual) incoming
                on (target.book_id = incoming.book_id)
                when matched then update set synopsis = :synopsis, embedding = :embedding
                when not matched then insert (book_id, synopsis, embedding)
                values (incoming.book_id, :synopsis, :embedding)
                """).param("bookId", bookId).param("synopsis", synopsis).param("embedding", vector).update());
    }

    public record SimilarBook(long bookId, double similarity) { }

    public List<SimilarBook> search(String text, int limit) {
        SqlParameterValue vector = embed(text);
        return jdbc.sql("""
                select book_id,
                       1 - vector_distance(embedding, :embedding, cosine) as similarity
                from book_embeddings
                where embedding is not null
                order by vector_distance(embedding, :embedding, cosine), book_id
                fetch first :limit rows only
                """).param("embedding", vector).param("limit", limit)
                .query((rs, row) -> new SimilarBook(rs.getLong("book_id"), rs.getDouble("similarity"))).list();
    }
}
