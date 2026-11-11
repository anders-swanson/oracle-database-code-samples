package com.example.reference.discovery;

import com.example.reference.book.BookService;
import com.example.reference.duality.BookDocumentService;
import com.example.reference.graph.BookGraphService;
import com.example.reference.spatial.BookstoreService;
import com.example.reference.vector.BookEmbeddingService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class DiscoveryService {
    // GRAPH_TABLE returns ordinary rows. Join them to native VECTORs, the spatial result,
    // relational stock, and the duality document in one statement and one consistent snapshot.
    private static final String DISCOVER_SQL = """
            with related as (%s),
                 nearby as (%s),
                 documents as (%s),
                 ranked as (
                     select b.id as book_id,
                            b.title,
                            n.store_id,
                            n.store_name,
                            n.distance_km,
                            ss.quantity,
                            vector_distance(v.embedding, :embedding, cosine) as semantic_distance,
                            d.document,
                            row_number() over (
                                partition by b.id
                                order by n.distance_km, n.store_id
                            ) as store_rank
                     from related r
                     join books b on b.id = r.candidate_id
                     join book_embeddings v on v.book_id = b.id and v.embedding is not null
                     join store_stock ss on ss.book_id = b.id and ss.quantity > 0
                     join nearby n on n.store_id = ss.store_id
                     join documents d on d.book_id = b.id
                 )
            select book_id,
                   title,
                   store_id,
                   store_name,
                   distance_km,
                   quantity,
                   1 - semantic_distance as similarity,
                   document
            from ranked
            where store_rank = 1
            order by semantic_distance, distance_km, book_id
            fetch first :limit rows only
            """.formatted(BookGraphService.CANDIDATES_SQL, BookstoreService.NEARBY_SQL,
                    BookDocumentService.DOCUMENTS_SQL);

    private final JdbcClient jdbc;
    private final BookEmbeddingService embeddings;
    private final BookService books;
    private final ObjectMapper json;

    public DiscoveryService(JdbcClient jdbc, BookEmbeddingService embeddings, BookService books, ObjectMapper json) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.books = books;
        this.json = json;
    }

    public record Match(long bookId, String title, long storeId, String storeName,
                        double distanceKm, int quantity, double similarity, JsonNode document) { }

    public List<Match> discover(long seedId, String query, double longitude, double latitude,
                                double radiusKm, int limit) {
        books.get(seedId);
        // Local model inference completes before the discovery statement acquires a connection.
        var embedding = embeddings.embed(query);
        return jdbc.sql(DISCOVER_SQL).param("seedId", seedId).param("embedding", embedding)
                .param("longitude", longitude).param("latitude", latitude)
                .param("radius", BookstoreService.radiusParameter(radiusKm)).param("limit", limit)
                .query((rs, row) -> new Match(rs.getLong("book_id"), rs.getString("title"),
                        rs.getLong("store_id"), rs.getString("store_name"), rs.getDouble("distance_km"),
                        rs.getInt("quantity"), rs.getDouble("similarity"), json.readTree(rs.getString("document"))))
                .list();
    }
}
