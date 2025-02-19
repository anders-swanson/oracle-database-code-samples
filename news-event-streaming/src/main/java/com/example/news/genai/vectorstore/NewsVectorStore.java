package com.example.news.genai.vectorstore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This sample class provides a vector abstraction for Oracle Database 23ai.
 * The sample class includes capabilities to create a table for embeddings, add embeddings, and execute similarity searches
 * against embeddings stored in the database.
 *
 * @author  Anders Swanson
 */
@Component
public class NewsVectorStore {
    /**
     * A batch size of 50 to 100 records is recommending for bulk inserts.
     */
    private static final int BATCH_SIZE = 50;


    /**
     * Vector table name to add/search Embeddings.
     */
    private final String tableName;

    private final VectorDataAdapter dataAdapter;

    public NewsVectorStore(
            @Value("${vectordb.tableName}") String tableName, VectorDataAdapter dataAdapter) {
        this.tableName = tableName;
        this.dataAdapter = dataAdapter;
    }

    /**
     * Adds an Embedding to the vector store.
     * @param embedding To add.
     * @param connection Database connection used for upsert.
     */
    public void add(Embedding embedding, Connection connection) {
        addAll(Collections.singletonList(embedding), connection);
    }

    /**
     * Adds a list of Embeddings to the vector store, in batches.
     * @param embeddings To add.
     * @param connection Database connection used for upsert.
     */
    public void addAll(List<Embedding> embeddings, Connection connection) {
        // Upsert is used in case of any conflicts.
        String upsert = String.format("""
                merge into %s target using (values(?, ?, ?)) source (id, content, embedding) on (target.id = source.id)
                when matched then update set target.content = source.content, target.embedding = source.embedding
                when not matched then insert (target.id, target.content, target.embedding) values (source.id, source.content, source.embedding)
                """, tableName);
        try (PreparedStatement stmt = connection.prepareStatement(upsert)) {
            for (int i = 0; i < embeddings.size(); i++) {
                Embedding embedding = embeddings.get(i);
                // Generate and set a random ID for the embedding.
                stmt.setString(1, UUID.randomUUID().toString());
                // Set the embedding text content if it exists.
                stmt.setString(2, embedding.content() != null ? embedding.content() : "");
                // When using the VECTOR data type with prepared statements, always use setObject with the OracleType.VECTOR targetSqlType.
                stmt.setObject(3, dataAdapter.toVECTOR(embedding.vector()), OracleType.VECTOR.getVendorTypeNumber());
                stmt.addBatch();

                // If BATCH_SIZE records have been added to the statement, execute the batch.
                if (i % BATCH_SIZE == BATCH_SIZE - 1) {
                    stmt.executeBatch();
                }
            }
            // if any remaining batches, execute them.
            if (embeddings.size() % BATCH_SIZE != 0) {
                stmt.executeBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Embedding> search(NewsSearchRequest searchRequest, Connection connection) {
        // This query is designed to:
        // 1. Calculate a similarity score for each row based on the cosine distance between the embedding column
        // and a given vector using the "vector_distance" function.
        // 2. Order the rows by this similarity score in descending order.
        // 3. Filter out rows with a similarity score below a specified threshold.
        // 4. Return only the top rows that meet the criteria.
        String searchQuery = String.format("""
                select * from (
                    select id, content, embedding, (1 - vector_distance(embedding, ?, COSINE)) as score
                    from %s
                    order by score desc
                )
                where score >= ?
                fetch first %d rows only
                """, tableName, searchRequest.getMaxResults());
        List<Embedding> matches = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(searchQuery)) {
            VECTOR searchVector = dataAdapter.toVECTOR(searchRequest.getVector());
            // When using the VECTOR data type with prepared statements, always use setObject with the OracleType.VECTOR targetSqlType.
            stmt.setObject(1, searchVector, OracleType.VECTOR.getVendorTypeNumber());
            stmt.setObject(2, searchRequest.getMinScore(), OracleType.NUMBER.getVendorTypeNumber());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double[] vector = rs.getObject("embedding", double[].class);
                    String content = rs.getObject("content", String.class);
                    Embedding embedding = new Embedding(dataAdapter.toFloatArray(vector), content);
                    matches.add(embedding);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return matches;
    }

    public int countEmbeddings(Connection connection) throws SQLException {
        final String sql = "select count(*) from " + tableName;
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }
}
