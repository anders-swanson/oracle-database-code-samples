package com.example.jms.queue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.QueueConnection;
import jakarta.jms.Session;
import javax.sql.DataSource;
import oracle.jakarta.jms.AQjmsFactory;
import oracle.jakarta.jms.AQjmsSession;
import oracle.jakarta.jms.AQjmsTextMessage;
import oracle.jdbc.OracleTypes;

public class QueueConsumer implements Runnable {
    private final int consumerID;
    private final DataSource dataSource;
    private final String username;
    private final String queueName;
    private final AtomicInteger count;

    public QueueConsumer(int consumerID, DataSource dataSource, String username, String queueName, AtomicInteger count) {
        this.consumerID = consumerID;
        this.dataSource = dataSource;
        this.username = username;
        this.queueName = queueName;
        this.count = count;
    }

    @Override
    public void run() {
        int consumedMessages = 0;
        // Create a new JMS connection and session.
        try (QueueConnection queueCon = AQjmsFactory.getQueueConnectionFactory(dataSource).createQueueConnection();
             AQjmsSession session = (AQjmsSession) queueCon.createQueueSession(true, Session.AUTO_ACKNOWLEDGE);
             Connection dbConn = session.getDBConnection()) {
            Queue queue = session.getQueue(username, queueName);
            // The JMS Connection must be started before use.
            queueCon.start();
            MessageConsumer consumer = session.createReceiver(queue);

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
                    System.out.printf("[CONSUMER %d] Received %d messages. Closing consumer!%n", consumerID, consumedMessages);
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
