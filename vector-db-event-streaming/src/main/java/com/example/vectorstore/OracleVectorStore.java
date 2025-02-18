package com.example.vectorstore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.example.model.Embedding;
import jakarta.annotation.PostConstruct;
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
public class OracleVectorStore {
    /**
     * A batch size of 50 to 100 records is recommending for bulk inserts.
     */
    private static final int BATCH_SIZE = 50;

    /**
     * DataSource connected to Oracle Database 23ai.
     */
    private final DataSource dataSource;
    /**
     * Vector table name to add/search Embeddings.
     */
    private final String tableName;
    /**
     * The embedding vector dimension size. This will be fixed for the embedding model used.
     */
    private final int dimensions;
    private final OracleDataAdapter dataAdapter = new OracleDataAdapter();

    public OracleVectorStore(DataSource dataSource,
                             @Value("${VECTOR_STORE_TABLE_NAME:vector_store}") String tableName,
                             @Value("${VECTOR_DIMENSIONS:1024}") int dimensions) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.dimensions = dimensions;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        // The "vector" type is used for the embedding column, and will store text embeddings.
        String createTableQuery = String.format("""
                create table if not exists %s (
                    id varchar2(36) default sys_guid() primary key,
                    content clob,
                    embedding vector(%d,FLOAT64) annotations(Distance 'COSINE', IndexType 'IVF')
                )
                """, tableName, dimensions);
        // An Inverted File (IVF) vector index is used to facilitate more efficient similarity search:
        // 1. It allows for quick retrieval of vectors that are similar to a given query vector.
        // 2. The index supports approximate nearest neighbor search with a given accuracy.
        // 4. The use of cosine distance makes it particularly suitable for tasks like semantic search of text embeddings.
        String createIndexQuery = String.format("""
                create vector index if not exists vector_index on %s (embedding)
                organization neighbor partitions
                distance COSINE
                with target accuracy 95
                parameters (type IVF, neighbor partitions 10)
                """, tableName);
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement();) {
            stmt.execute(createTableQuery);
            stmt.execute(createIndexQuery);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds an Embedding to the vector store.
     * @param embedding To add.
     */
    public void add(Embedding embedding) {
        addAll(Collections.singletonList(embedding));
    }


    /**
     * Adds a list of Embeddings to the vector store, in batches.
     * @param embeddings To add.
     */
    public void addAll(List<Embedding> embeddings) {
        // Upsert is used in case of any conflicts.
        String upsert = String.format("""
                merge into %s target using (values(?, ?, ?)) source (id, content, embedding) on (target.id = source.id)
                when matched then update set target.content = source.content, target.embedding = source.embedding
                when not matched then insert (target.id, target.content, target.embedding) values (source.id, source.content, source.embedding)
                """, tableName);
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(upsert)) {
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

    public List<Embedding> search(SearchRequest searchRequest) {
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
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(searchQuery)) {
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

    public int countEmbeddings() {
        final String sql = "select count(*) from " + tableName;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public void reset() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();) {
            stmt.executeQuery("drop table " + tableName);
            createTableIfNotExists();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
