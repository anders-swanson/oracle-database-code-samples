package com.example.support;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.support.model.SimilarIncident;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketSearchService {
    private static final String TICKET_CHUNK_TYPE = "TICKET";
    private static final String RUNBOOK_CHUNK_TYPE = "RUNBOOK";

    // Loads the relational ticket fields plus JSON payload/product attributes needed for search context.
    private static final String TICKET_CONTEXT_SQL = """
            select t.ticket_id,
                   t.subject,
                   t.body,
                   json_value(t.payload, '$.errorCode') as error_code,
                   json_value(t.payload, '$.sku') as sku,
                   json_value(p.specs, '$.family') as product_family,
                   p.name as product_name
            from support_tickets t
            join products p on p.product_id = t.product_id
            where t.ticket_id = ?
            """;
    // Clears existing generated chunks before rebuilding ticket and runbook embeddings.
    private static final String DELETE_CHUNKS_SQL = "delete from ticket_chunks where ticket_id = ?";
    // Stores searchable text chunks with their VECTOR embedding for later similarity search.
    private static final String INSERT_CHUNK_SQL = """
            insert into ticket_chunks (ticket_id, chunk_type, chunk_text, embedding)
            values (?, ?, ?, ?)
            """;
    // Retrieves runbook text that matches the ticket's product family and JSON error code.
    private static final String RUNBOOK_SQL = """
            select title, body
            from runbooks
            where product_family = ?
              and error_code = ?
            order by runbook_id
            """;
    // Combines relational filters, JSON search, and VECTOR distance to rank related incidents.
    private static final String SIMILAR_INCIDENTS_SQL = """
            with ranked as (
                select t.ticket_id,
                       t.subject,
                       c.name as customer_name,
                       c.tier as customer_tier,
                       p.name as product_name,
                       json_value(p.specs, '$.family') as product_family,
                       t.sla_status,
                       (1 - vector_distance(tc.embedding, ?, COSINE)) as score,
                       score(1) as text_score,
                       row_number() over (
                           partition by t.ticket_id
                           order by vector_distance(tc.embedding, ?, COSINE)
                       ) as rn
                from ticket_chunks tc
                join support_tickets t on t.ticket_id = tc.ticket_id
                join customers c on c.customer_id = t.customer_id
                join products p on p.product_id = t.product_id
                join customer_orders o on o.order_id = t.order_id
                where t.ticket_id <> ?
                  and c.tier = ?
                  and t.sla_status = ?
                  and json_value(p.specs, '$.family') = ?
                  and json_value(p.specs, '$.sku') = ?
                  and o.order_status in ('OPEN', 'SHIPPED')
                  and json_textcontains(t.payload, '$', ?, 1)
            )
            select ticket_id,
                   subject,
                   customer_name,
                   customer_tier,
                   product_name,
                   product_family,
                   sla_status,
                   score,
                   text_score
            from ranked
            where rn = 1
            order by score desc, ticket_id
            fetch first 5 rows only
            """;
    // Reads the ticket as a document from the JSON-relational duality view.
    private static final String DOCUMENT_SQL = """
            select json_serialize(data returning clob)
            from tickets_dv dv
            where dv.data."_id" = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final VectorService vectorService;

    TicketSearchService(JdbcTemplate jdbcTemplate, VectorService vectorService) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorService = vectorService;
    }

    public void enrichTicket(Connection connection, long ticketId) {
        try {
            Optional<TicketContext> context = findTicketContext(connection, ticketId);
            if (context.isEmpty()) {
                return;
            }

            rebuildSearchChunks(connection, context.get());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to enrich ticket " + ticketId, exception);
        }
    }

    /**
     * Finds related support incidents by combining the current ticket embedding with relational filters,
     * JSON text search over ticket payloads, and VECTOR distance over generated ticket chunks.
     *
     * @param ticketId the ticket to use as the search query
     * @param customerTier limits results to customers in the same support tier
     * @param slaStatus limits results to tickets with the requested SLA status
     * @return the highest-ranked matching incidents, ordered by vector similarity
     */
    List<SimilarIncident> findSimilarIncidents(long ticketId, String customerTier, String slaStatus) {
        return jdbcTemplate.execute((Connection connection) -> {
            TicketContext context = findTicketContext(connection, ticketId)
                    .orElseThrow(() -> new SQLException("Ticket " + ticketId + " does not exist"));
            VECTOR queryVector = vectorService.embed(ticketSearchText(context));
            try (PreparedStatement statement = connection.prepareStatement(SIMILAR_INCIDENTS_SQL)) {
                statement.setObject(1, queryVector, OracleType.VECTOR.getVendorTypeNumber());
                statement.setObject(2, queryVector, OracleType.VECTOR.getVendorTypeNumber());
                statement.setLong(3, ticketId);
                statement.setString(4, customerTier);
                statement.setString(5, slaStatus);
                statement.setString(6, context.productFamily());
                statement.setString(7, context.sku());
                statement.setString(8, context.errorCode());
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<SimilarIncident> incidents = new ArrayList<>();
                    while (resultSet.next()) {
                        incidents.add(mapSimilarIncident(resultSet));
                    }
                    return incidents;
                }
            }
        });
    }

    /**
     * Returns the ticket document produced by the JSON-relational duality view.
     *
     * @param ticketId the ticket to read as a document
     * @return the serialized ticket document JSON
     */
    String findTicketDocument(long ticketId) {
        return jdbcTemplate.queryForObject(
                DOCUMENT_SQL,
                (resultSet, rowNum) -> clobToString(resultSet.getClob(1)),
                ticketId
        );
    }

    private void rebuildSearchChunks(Connection connection, TicketContext context) throws SQLException {
        deleteChunks(connection, context.ticketId());
        insertChunk(connection, context.ticketId(), TICKET_CHUNK_TYPE, ticketSearchText(context));
        for (String runbookChunk : runbookChunks(connection, context.productFamily(), context.errorCode())) {
            insertChunk(connection, context.ticketId(), RUNBOOK_CHUNK_TYPE, runbookChunk);
        }
    }

    private void deleteChunks(Connection connection, long ticketId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_CHUNKS_SQL)) {
            statement.setLong(1, ticketId);
            statement.executeUpdate();
        }
    }

    private Optional<TicketContext> findTicketContext(Connection connection, long ticketId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TICKET_CONTEXT_SQL)) {
            statement.setLong(1, ticketId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new TicketContext(
                            resultSet.getLong("ticket_id"),
                            resultSet.getString("subject"),
                            resultSet.getString("body"),
                            resultSet.getString("error_code"),
                            resultSet.getString("sku"),
                            resultSet.getString("product_family"),
                            resultSet.getString("product_name")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private void insertChunk(Connection connection, long ticketId, String chunkType, String chunkText) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHUNK_SQL)) {
            statement.setLong(1, ticketId);
            statement.setString(2, chunkType);
            statement.setString(3, chunkText);
            statement.setObject(4, vectorService.embed(chunkText), OracleType.VECTOR.getVendorTypeNumber());
            statement.executeUpdate();
        }
    }

    private List<String> runbookChunks(Connection connection, String productFamily, String errorCode) throws SQLException {
        List<String> chunks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(RUNBOOK_SQL)) {
            statement.setString(1, productFamily);
            statement.setString(2, errorCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chunks.add("Runbook: " + resultSet.getString("title") + ". " + resultSet.getString("body"));
                }
            }
        }
        return chunks;
    }

    private SimilarIncident mapSimilarIncident(ResultSet resultSet) throws SQLException {
        return new SimilarIncident(
                resultSet.getLong("ticket_id"),
                resultSet.getString("subject"),
                resultSet.getString("customer_name"),
                resultSet.getString("customer_tier"),
                resultSet.getString("product_name"),
                resultSet.getString("product_family"),
                resultSet.getString("sla_status"),
                resultSet.getDouble("score"),
                resultSet.getInt("text_score")
        );
    }

    private String ticketSearchText(TicketContext ticket) {
        return """
                Ticket %d for %s. Product %s with SKU %s and error %s. Subject: %s. Body: %s
                """.formatted(
                ticket.ticketId(),
                ticket.productFamily(),
                ticket.productName(),
                ticket.sku(),
                ticket.errorCode(),
                ticket.subject(),
                ticket.body()
        );
    }

    private String clobToString(Clob clob) throws SQLException {
        return clob.getSubString(1, Math.toIntExact(clob.length()));
    }

    private record TicketContext(
            long ticketId,
            String subject,
            String body,
            String errorCode,
            String sku,
            String productFamily,
            String productName
    ) {
    }
}
