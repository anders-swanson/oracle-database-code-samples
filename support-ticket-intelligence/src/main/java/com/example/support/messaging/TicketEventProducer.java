package com.example.support.messaging;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.support.model.TicketOpenedEvent;
import com.example.support.model.TicketRequest;
import com.example.support.model.TicketResponse;
import com.oracle.spring.json.jsonb.JSONB;
import jakarta.annotation.PostConstruct;
import oracle.jdbc.OracleTypes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TicketEventProducer {
    private static final String INSERT_TICKET_SQL = """
            insert into support_tickets (
                customer_id, order_id, product_id, subject, body, status, sla_status, payload
            ) values (?, ?, ?, ?, ?, 'OPEN', ?, ?)
            """;
    private static final String UPDATE_PAYLOAD_SQL = """
            update support_tickets
            set payload = ?
            where ticket_id = ?
            """;
    private static final String INSERT_EDGE_SQL = """
            insert into ticket_product_edges (ticket_id, product_id, relationship)
            values (?, ?, 'AFFECTS')
            """;
    private static final String PRODUCT_SKU_SQL = "select json_value(specs, '$.sku') from products where product_id = ?";

    private final KafkaProducer<String, TicketOpenedEvent> producer;
    private final String topicName;
    private final JSONB jsonb;

    public TicketEventProducer(
            KafkaProducer<String, TicketOpenedEvent> producer,
            @Value("${support.topic.ticket-opened}") String topicName, JSONB jsonb
    ) {
        this.producer = producer;
        this.topicName = topicName;
        this.jsonb = jsonb;
    }

    @PostConstruct
    public void initTransactions() {
        producer.initTransactions();
    }

    public TicketResponse openTicket(TicketRequest request) {
        producer.beginTransaction();
        try {
            Connection connection = producer.getDBConnection();
            long ticketId = createTicket(connection, request);
            publishTicketOpened(ticketId);
            producer.commitTransaction();
            return new TicketResponse(ticketId, "OPEN");
        } catch (Exception exception) {
            producer.abortTransaction();
            throw new IllegalStateException("Unable to create support ticket and publish event", exception);
        }
    }

    private long createTicket(Connection connection, TicketRequest request) throws SQLException {
        String sku = productSku(connection, request.productId());
        long ticketId = insertTicket(connection, request, sku);
        updateTicketPayload(connection, ticketId, request, sku);
        insertTicketProductEdge(connection, ticketId, request.productId());
        return ticketId;
    }

    private void publishTicketOpened(long ticketId) {
        producer.send(new ProducerRecord<>(
                topicName,
                Long.toString(ticketId),
                new TicketOpenedEvent(ticketId)
        ));
    }

    private long insertTicket(Connection connection, TicketRequest request, String sku) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TICKET_SQL, new String[]{"TICKET_ID"})) {
            statement.setLong(1, request.customerId());
            statement.setLong(2, request.orderId());
            statement.setLong(3, request.productId());
            statement.setString(4, request.subject());
            statement.setString(5, request.body());
            statement.setString(6, request.slaStatus());
            statement.setObject(7, jsonb.toOSON(payload(request, sku)), OracleTypes.JSON);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        }
        throw new SQLException("Ticket insert did not return a generated key");
    }

    private void updateTicketPayload(Connection connection, long ticketId, TicketRequest request, String sku)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_PAYLOAD_SQL)) {
            statement.setObject(1, jsonb.toOSON(payload(ticketId, request, sku)), OracleTypes.JSON);
            statement.setLong(2, ticketId);
            statement.executeUpdate();
        }
    }

    private void insertTicketProductEdge(Connection connection, long ticketId, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EDGE_SQL)) {
            statement.setLong(1, ticketId);
            statement.setLong(2, productId);
            statement.executeUpdate();
        }
    }

    private Map<String, Object> payload(TicketRequest request, String sku) {
        Map<String, Object> payload = new LinkedHashMap<>();
        addTicketPayloadFields(payload, request, sku);
        return payload;
    }

    private Map<String, Object> payload(long ticketId, TicketRequest request, String sku) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketCode", "TCK" + ticketId);
        addTicketPayloadFields(payload, request, sku);
        return payload;
    }

    private void addTicketPayloadFields(Map<String, Object> payload, TicketRequest request, String sku) {
        payload.put("subject", request.subject());
        payload.put("body", request.body());
        payload.put("errorCode", request.errorCode());
        payload.put("severity", request.severity());
        payload.put("sku", sku);
        payload.put("source", "customer-portal");
    }

    private String productSku(Connection connection, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PRODUCT_SKU_SQL)) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            }
        }
        throw new SQLException("Product " + productId + " does not exist");
    }
}
