package com.example.memory.search;

import com.example.memory.EmbeddingClient;
import com.example.memory.model.MemoryDocument;
import com.example.memory.model.MemoryHit;
import jakarta.json.stream.JsonParser;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.VECTOR;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MemoryRepository {

    private static final String CREATE_TABLE_SQL = """
            create table if not exists agent_memories (
                id number generated always as identity primary key,
                memory_kind varchar2(30) not null,
                title varchar2(200) not null,
                memory_doc json not null,
                embedding vector(%d, FLOAT32) annotations(Distance 'COSINE', IndexType 'IVF')
            )
            """;
    private static final String CREATE_VECTOR_INDEX_SQL = """
            create vector index if not exists agent_memories_vector_idx
            on agent_memories (embedding)
            organization neighbor partitions
            distance COSINE
            with target accuracy 95
            parameters (type IVF, neighbor partitions 8)
            """;
    private static final String CREATE_SEARCH_INDEX_SQL = """
            create search index if not exists agent_memories_search_idx
            on agent_memories (memory_doc)
            for json
            parameters ('sync (on commit) search_on text')
            """;
    private static final String INSERT_SQL = """
            insert into agent_memories (memory_kind, title, memory_doc, embedding)
            values (?, ?, ?, ?)
            """;
    private static final String COUNT_SQL = "select count(*) from agent_memories";
    private static final String VECTOR_SEARCH_SQL = """
            select id,
                   memory_kind,
                   title,
                   memory_doc,
                   (1 - vector_distance(embedding, ?, COSINE)) as vector_score
            from agent_memories
            order by vector_score desc, id
            fetch first ? rows only
            """;
    private static final String TEXT_SEARCH_SQL = """
            select id,
                   memory_kind,
                   title,
                   memory_doc,
                   score(1) as text_score
            from agent_memories
            where json_textcontains(memory_doc, '$', ?, 1)
            order by score(1) desc, id
            fetch first ? rows only
            """;
    private static final int BRANCH_LIMIT = 5;

    private final DataSource dataSource;
    private final EmbeddingClient embeddingClient;
    private final OSONMapper osonMapper;
    private final MemorySearchRanker searchRanker;

    public MemoryRepository(DataSource dataSource, EmbeddingClient embeddingClient) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingGateway is required");
        this.osonMapper = OSONMapper.createDefault();
        this.searchRanker = new MemorySearchRanker();
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            execute(connection, String.format(Locale.US, CREATE_TABLE_SQL, embeddingClient.dimensions()));
            execute(connection, CREATE_VECTOR_INDEX_SQL);
            execute(connection, CREATE_SEARCH_INDEX_SQL);
            connection.commit();
        }
    }

    public void seedIfEmpty(List<MemoryDocument> records) {
        if (count() > 0) {
            return;
        }
        for (MemoryDocument document : records) {
            storeMemory(document);
        }
    }

    public long storeMemory(MemoryDocument document) {
        byte[] oson = osonMapper.toOSON(document);
        VECTOR vector = embeddingClient.embedToVECTOR(document.searchableText());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, new String[]{"id"})) {
            statement.setString(1, document.getMemoryKind());
            statement.setString(2, document.getTitle());
            statement.setObject(3, oson, OracleTypes.JSON);
            statement.setObject(4, vector, OracleType.VECTOR.getVendorTypeNumber());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new IllegalStateException("No generated key returned for stored memory.");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<MemoryHit> combinedSearch(MemorySearchRequest request) {
        QueryHints hints = QueryHintExtractor.extract(request.question());
        List<MemoryHit> vectorHits = vectorSearch(request.question(), BRANCH_LIMIT);
        List<MemoryHit> textHits = textSearch(buildTextExpression(hints), BRANCH_LIMIT);
        return searchRanker.fuse(vectorHits, textHits, hints, request.maxResults());
    }

    public List<MemoryHit> vectorSearch(String question, int maxResults) {
        VECTOR vector = embeddingClient.embedToVECTOR(question);
        List<MemoryHit> hits = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(VECTOR_SEARCH_SQL)) {
            statement.setObject(1, vector, OracleType.VECTOR.getVendorTypeNumber());
            statement.setInt(2, maxResults);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    hits.add(mapHit(resultSet, resultSet.getDouble("vector_score"), 0, "VECTOR"));
                }
            }
            return hits;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<MemoryHit> textSearch(String expression, int maxResults) {
        List<MemoryHit> hits = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(TEXT_SEARCH_SQL)) {
            statement.setString(1, expression);
            statement.setInt(2, maxResults);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    hits.add(mapHit(resultSet, 0.0d, resultSet.getInt("text_score"), "TEXT"));
                }
            }
            return hits;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildTextExpression(QueryHints hints) {
        if (hints.keywords().isEmpty()) {
            return "memory";
        }
        return String.join(" ACCUM ", hints.keywords());
    }

    private MemoryHit mapHit(ResultSet resultSet, double vectorScore, int textScore, String matchedBy) throws SQLException {
        JsonParser parser = resultSet.getObject("memory_doc", JsonParser.class);
        MemoryDocument document = osonMapper.fromOSON(parser, MemoryDocument.class);
        return new MemoryHit(
                resultSet.getLong("id"),
                document.getMemoryKind(),
                document.getTitle(),
                document.summary(),
                document.searchableText(),
                document.service(),
                document.environment(),
                document.incidentId(),
                document.changeTicket(),
                vectorScore,
                textScore,
                0.0d,
                matchedBy
        );
    }

    private long count() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void createSearchIndex(Connection connection) {
        try {
            execute(connection, CREATE_SEARCH_INDEX_SQL);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
