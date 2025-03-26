package com.example.jms.topic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final String groupName;
    private final AtomicInteger count;

    public JMSConsumer(DataSource dataSource, int consumerID, String groupName, String username, String topicName, AtomicInteger count) {
        this.dataSource = dataSource;
        this.consumerID = consumerID;
        this.username = username;
        this.topicName = topicName;
        this.groupName = groupName;
        this.count = count;
    }

    @Override
    public void run() {
        int consumedMessages = 0;
        // Create a new JMS connection and session.
        try (TopicConnection topicConn = AQjmsFactory.getTopicConnectionFactory(dataSource).createTopicConnection();
             AQjmsSession session = (AQjmsSession) topicConn.createTopicSession(true, Session.AUTO_ACKNOWLEDGE);
             Connection dbConn = session.getDBConnection()) {
            Topic jmsTopic = session.getTopic(username, topicName);
            // The JMS Connection must be started before use.
            topicConn.start();
            MessageConsumer consumer = session.createDurableSubscriber(jmsTopic, groupName);

            while (true) {
                AQjmsTextMessage message = (AQjmsTextMessage) consumer.receive(1_000); // Timeout: 1 second
                if (message != null) {
                    // The atomic count abstraction is for example purposes only.
                    // We want to stop all the consumers after the count reaches 0.
                    if (count.decrementAndGet() >= 0) {
                        String msg = message.getText();
                        processMessage(msg, dbConn);
                        session.commit();  // Only commit if message received and processed successfully
                        consumedMessages++;
                    }
                }

                if (count.get() <= 0) {
                    System.out.printf("[CONSUMER %d (%s)] Received %d JMS messages. Closing consumer!%n", consumerID, groupName, consumedMessages);
                    return;
                }
            }
        } catch (JMSException | SQLException e) {
            System.out.println("Exception caught: " + e);
            throw new RuntimeException(e);
        }
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
