package com.example.fraud;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.oracle.spring.json.jsonb.JSONB;
import oracle.jdbc.OracleTypes;
import oracle.spatial.geometry.JGeometry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.oracle.okafka.clients.producer.KafkaProducer;

public class CardTransactionProducer {
    private final KafkaProducer<String, CardChargeEvent> producer;
    private final String topic;
    private final JSONB jsonb = JSONB.createDefault();

    public CardTransactionProducer(KafkaProducer<String, CardChargeEvent> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
        producer.initTransactions();
    }

    public void produce(List<CardChargeEvent> events) {
        for (CardChargeEvent event : events) {
            producer.beginTransaction();
            try {
                long id = insertCardCharge(producer.getDBConnection(), event);
                event.setTransactionId(id);
                producer.send(new ProducerRecord<>(topic, event));
                producer.commitTransaction();
            } catch (Exception exception) {
                producer.abortTransaction();
                throw new IllegalStateException("Unable to publish card charge event", exception);
            }
        }
    }

    private long insertCardCharge(Connection connection, CardChargeEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into card_transactions (
                    cardholder_id, occurred_at, amount, currency, merchant_name,
                    merchant_category, channel, device_id, location
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[]{"TRANSACTION_ID"})) {
            statement.setLong(1, event.getCardholderId());
            statement.setObject(2, asTimestamp(event.getOccurredAt()));
            statement.setDouble(3, event.getAmount());
            statement.setString(4, event.getCurrency());
            statement.setString(5, event.getMerchantName());
            statement.setString(6, event.getMerchantCategory());
            statement.setString(7, event.getChannel());
            statement.setString(8, event.getDeviceId());
            statement.setObject(9, JGeometry.storeJS(point(event), connection), OracleTypes.STRUCT);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        }
        throw new SQLException("Ticket insert did not return a generated key");
    }

    private JGeometry point(CardChargeEvent event) {
        return JGeometry.createPoint(new double[]{event.getLongitude(), event.getLatitude()}, 2, 8307);
    }

    private OffsetDateTime asTimestamp(String occurredAt) {
        return OffsetDateTime.ofInstant(Instant.parse(occurredAt), ZoneOffset.UTC);
    }
}
