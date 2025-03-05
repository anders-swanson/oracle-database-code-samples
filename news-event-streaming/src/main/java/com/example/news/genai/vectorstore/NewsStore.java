package com.example.news.genai.vectorstore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.example.news.model.News;
import com.oracle.spring.json.jsonb.JSONB;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.VECTOR;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.stereotype.Component;

/**
 * This sample class provides a vector abstraction for Oracle Database 23ai.
 * The sample class includes capabilities to create a table for embeddings, add embeddings, and execute similarity searches
 * against embeddings stored in the database.
 *
 * @author  Anders Swanson
 */
@Component
public class NewsStore {
    /**
     * A batch size of 50 to 100 records is recommending for bulk inserts.
     */
    private static final int BATCH_SIZE = 50;

    private final VectorDataAdapter dataAdapter;
    private final JSONB jsonb;

    public NewsStore(VectorDataAdapter dataAdapter, JSONB jsonb) {
        this.dataAdapter = dataAdapter;
        this.jsonb = jsonb;
    }

    /**
     * Adds a list of Embeddings to the vector store, in batches.
     * @param newsList To add.
     * @param connection Database connection used for upsert.
     */
    public void addAll(ConsumerRecords<String, News> newsList, Connection connection) {
        final String sql = """
                insert into news_dv (data) values(?)
                """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int i = 0;
            for (ConsumerRecord<String, News> record : newsList) {
                News news = record.value();
                byte[] oson = jsonb.toOSON(news);

                stmt.setObject(1, oson, OracleTypes.JSON);
                stmt.addBatch();

                // If BATCH_SIZE records have been added to the statement, execute the batch.
                if (i % BATCH_SIZE == BATCH_SIZE - 1) {
                    stmt.executeBatch();
                }
                i++;
            }
            // If there are any remaining batches, execute them.
            if (newsList.count() % BATCH_SIZE != 0) {
                stmt.executeBatch();
            }
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
                    from news_vector
                    order by score desc
                )
                where score >= ?
                fetch first %d rows only
                """, searchRequest.getMaxResults());
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
                    Embedding embedding = new Embedding(dataAdapter.toVECTOR(vector), content);
                    matches.add(embedding);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return matches;
    }

    public int countEmbeddings(Connection connection) throws SQLException {
        final String sql = "select count(*) from news_dv";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }
}
