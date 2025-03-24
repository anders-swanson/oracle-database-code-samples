package com.example.jms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import javax.sql.DataSource;
import oracle.jakarta.jms.AQjmsException;
import oracle.jakarta.jms.AQjmsFactory;
import oracle.jakarta.jms.AQjmsSession;
import oracle.jakarta.jms.AQjmsTextMessage;
import oracle.jdbc.OracleTypes;

public class JMSConsumer implements Runnable {
    private final DataSource dataSource;
    private final int consumerID;
    private final String username;
    private final String topicName;
    private final CountDownLatch latch;

    public JMSConsumer(DataSource dataSource, int consumerID, String username, String topicName, CountDownLatch latch) {
        this.dataSource = dataSource;
        this.consumerID = consumerID;
        this.username = username;
        this.topicName = topicName;
        this.latch = latch;
    }

    @Override
    public void run() {
        // Create a new JMS connection and session.
        try (TopicConnection topicConn = AQjmsFactory.getTopicConnectionFactory(dataSource).createTopicConnection();
             AQjmsSession session = (AQjmsSession) topicConn.createTopicSession(true, Session.AUTO_ACKNOWLEDGE);
             Connection dbConn = session.getDBConnection()) {
            Topic jmsTopic = session.getTopic(username, topicName);
            // The JMS Connection must be started before use.
            topicConn.start();
            MessageConsumer consumer = session.createDurableSubscriber(jmsTopic, "example_subscriber");

            while (latch.getCount() > 0) {
                AQjmsTextMessage message = (AQjmsTextMessage) consumer.receive(1_000); // Timeout: 1 second
                if (message != null) {
                    latch.countDown();
                    String msg = message.getText();
                    processMessage(msg, dbConn);
                    session.commit();  // Only commit if message received and processed successfully
                }
            }

        } catch (AQjmsException e) {
            System.out.println("Topic is empty, closing");
        } catch (JMSException | SQLException e) {
            System.out.println("Exception caught: " + e);
            throw new RuntimeException(e);
        }

        System.out.printf("[CONSUMER %d] Received all JMS messages. Closing consumer!%n", consumerID);
    }

    private void processMessage(String message, Connection dbConn) throws SQLException {
        final String sql = """
                insert into weather_events (data) values(?)
                """;

        try (PreparedStatement stmt = dbConn.prepareStatement(sql)) {
            stmt.setObject(1, message.getBytes(), OracleTypes.JSON);
            stmt.executeUpdate();
        }
    }

}
