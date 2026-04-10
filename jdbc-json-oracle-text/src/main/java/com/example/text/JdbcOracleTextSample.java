package com.example.text;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonParser;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.datasource.impl.OracleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class JdbcOracleTextSample {
    private static final String DOCUMENTS_RESOURCE = "/documents.json";

    private static final String INSERT_SQL = """
            insert into json_documents (search_document)
            values (?)
            """;
    private static final String KEYWORD_SEARCH_SQL = """
            select id,
                   search_document,
                   score(1) as score
            from json_documents
            where json_textcontains(search_document, '$', ?, 1)
            order by score(1) desc, id
            """;
    private static final String PROXIMITY_SEARCH_SQL = """
            select id,
                   search_document,
                   score(2) as score
            from json_documents
            where json_textcontains(search_document, '$', ?, 2)
            order by score(2) desc, id
            """;
    private static final String FILTERED_SEARCH_SQL = """
            select id,
                   search_document,
                   score(3) as score
            from json_documents
            where json_textcontains(search_document, '$', ?, 3)
              and json_value(search_document, '$.category' returning varchar2(30)) = ?
              and json_value(search_document, '$.author' returning varchar2(30)) = ?
            order by score(3) desc, id
            """;

    private final DataSource dataSource;

    public JdbcOracleTextSample(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected arguments: <jdbc-url> <jdbc-user> <jdbc-password>");
        }

        JdbcOracleTextSample sample = new JdbcOracleTextSample(createDataSource(args[0], args[1], args[2]));
        sample.run();
    }

    void run() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            List<TextDocument> documents = loadDocuments(DOCUMENTS_RESOURCE);
            insertDocuments(connection, documents);
            connection.commit();

            System.out.printf(
                    Locale.US,
                    "Loaded %d JSON documents into JSON_DOCUMENTS and committed them.%n",
                    documents.size()
            );
            System.out.println("The Oracle Text JSON search index can now rank matching documents.");
            System.out.println();

            List<SearchHit> keywordHits = search(connection, KEYWORD_SEARCH_SQL, "oracle");
            List<SearchHit> proximityHits = search(connection, PROXIMITY_SEARCH_SQL, "NEAR((json, search), 3)");
            List<SearchHit> filteredHits = search(
                    connection,
                    FILTERED_SEARCH_SQL,
                    "oracle",
                    "GUIDE",
                    "Ava"
            );

            validateExpectedResults(documents, keywordHits, proximityHits, filteredHits);

            printResults(
                    "Keyword search",
                    "oracle",
                    "Find documents whose indexed JSON text contains the token 'oracle'.",
                    keywordHits
            );
            printResults(
                    "Proximity search",
                    "NEAR((json, search), 3)",
                    "Find documents where 'json' and 'search' appear within 3 tokens of each other.",
                    proximityHits
            );
            printResults(
                    "Mixed search",
                    "oracle with category = GUIDE and author = Ava",
                    "Find documents whose indexed JSON text contains 'oracle', then keep only JSON documents where category is GUIDE and author is Ava.",
                    filteredHits
            );
        }
    }

    static List<TextDocument> loadDocuments(String resourcePath) throws IOException {
        try (InputStream stream = JdbcOracleTextSample.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Document resource not found: " + resourcePath);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                 JsonReader jsonReader = Json.createReader(reader)) {
                JsonArray array = jsonReader.readArray();
                List<TextDocument> documents = new ArrayList<>();
                for (int i = 0; i < array.size(); i++) {
                    JsonObject object = array.getJsonObject(i);
                    documents.add(new TextDocument(
                            object.getString("title"),
                            object.getString("summary"),
                            object.getString("body"),
                            object.getString("category"),
                            object.getString("author")
                    ));
                }
                return documents;
            }
        }
    }

    static void insertDocuments(Connection connection, List<TextDocument> documents) throws SQLException {
        OSONMapper oson = OSONMapper.createDefault();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            for (TextDocument document : documents) {
                statement.setObject(1, oson.toOSON(document), OracleTypes.JSON);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    static List<SearchHit> search(Connection connection, String sql, String... parameters) throws SQLException {
        List<SearchHit> hits = new ArrayList<>();
        OSONMapper oson = OSONMapper.createDefault();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    JsonParser parser = resultSet.getObject("search_document", JsonParser.class);
                    hits.add(new SearchHit(
                            resultSet.getLong("id"),
                            oson.fromOSON(parser, TextDocument.class),
                            resultSet.getInt("score")
                    ));
                }
            }
        }
        return hits;
    }

    static void validateExpectedResults(
            List<TextDocument> documents,
            List<SearchHit> keywordHits,
            List<SearchHit> proximityHits,
            List<SearchHit> filteredHits
    ) {
        if (documents.size() != 4) {
            throw new IllegalStateException("Expected 4 JSON documents but found " + documents.size());
        }
        if (keywordHits.size() != 3) {
            throw new IllegalStateException("Expected 3 keyword hits but found " + keywordHits.size());
        }
        if (!"Oracle Text for JSON Search".equals(keywordHits.getFirst().document().title())) {
            throw new IllegalStateException("Expected first keyword hit to be Oracle Text for JSON Search but was " + keywordHits.getFirst().document().title());
        }
        if (proximityHits.size() != 1) {
            throw new IllegalStateException("Expected 1 proximity hit but found " + proximityHits.size());
        }
        if (!"Oracle Text for JSON Search".equals(proximityHits.getFirst().document().title())) {
            throw new IllegalStateException("Expected proximity hit to be Oracle Text for JSON Search but was " + proximityHits.getFirst().document().title());
        }
        if (filteredHits.size() != 1) {
            throw new IllegalStateException("Expected 1 filtered hit but found " + filteredHits.size());
        }
        if (!"Oracle Text for JSON Search".equals(filteredHits.getFirst().document().title())) {
            throw new IllegalStateException("Expected filtered hit to be Oracle Text for JSON Search but was " + filteredHits.getFirst().document().title());
        }
    }

    static void printResults(String heading, String expression, String explanation, List<SearchHit> hits) {
        System.out.printf("%s using json_textcontains(..., \"%s\")%n", heading, expression);
        System.out.println(explanation);
        System.out.println("Oracle Text SCORE is a relevance ranking for this query only.");
        System.out.println("A higher score means a stronger match in this result set. It is not a percentage.");

        if (hits.isEmpty()) {
            System.out.println("No documents matched this query.");
            System.out.println();
            return;
        }

        System.out.printf(
                Locale.US,
                "%d document(s) matched. Results are ordered by descending SCORE.%n",
                hits.size()
        );

        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            System.out.printf(
                    Locale.US,
                    "%d. %s | category=%s | author=%s | score=%d | %s%n",
                    i + 1,
                    hit.document().title(),
                    hit.document().category(),
                    hit.document().author(),
                    hit.score(),
                    describeScore(i, hit.score())
            );
        }
        System.out.println();
    }

    static String describeScore(int index, int score) {
        if (index == 0) {
            return "top-ranked match for this query";
        }
        return "less relevant than the top result for this query";
    }

    static OracleDataSource createDataSource(String url, String username, String password) throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    public record TextDocument(String title, String summary, String body, String category, String author) {
    }

    public record SearchHit(long id, TextDocument document, int score) {
    }
}
