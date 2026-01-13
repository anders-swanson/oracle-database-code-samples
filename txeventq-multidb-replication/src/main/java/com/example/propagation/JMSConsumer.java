package com.example.propagation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import javax.sql.DataSource;

import oracle.jakarta.jms.AQjmsFactory;
import oracle.jakarta.jms.AQjmsSession;
import oracle.jakarta.jms.AQjmsTextMessage;
import oracle.jdbc.OracleTypes;

public class JMSConsumer implements Runnable {
    private final DataSource dataSource;
    private final String username;
    private final String topicName;
    private final String groupName;

    public static void main(String[] args) {
        final String username = "destuser";
        // connect to the destination database
        DataSource ds = DataSourceFactory.create(username, 1523);
        new JMSConsumer(ds, "destination_grp", username, "dest").run();
    }

    public JMSConsumer(DataSource dataSource, String groupName, String username, String topicName) {
        this.dataSource = dataSource;
        this.username = username;
        this.topicName = topicName;
        this.groupName = groupName;
    }

    @Override
    public void run() {
        // Create a new JMS connection and session.
        try (TopicConnection topicConn = AQjmsFactory.getTopicConnectionFactory(dataSource).createTopicConnection();
             AQjmsSession session = (AQjmsSession) topicConn.createTopicSession(true, Session.AUTO_ACKNOWLEDGE)) {
            Topic jmsTopic = session.getTopic(username, topicName);
            // The JMS Connection must be started before use.
            topicConn.start();
            MessageConsumer consumer = session.createDurableSubscriber(jmsTopic, groupName);

            System.out.printf("Subscribed to topic '%s.%s', waiting for messages...\n", username, topicName);

            while (true) {
                AQjmsTextMessage message = (AQjmsTextMessage) consumer.receive(1_000); // Timeout: 1 second
                if (message != null) {
                    String msg = message.getText();
                    System.out.printf("Received: %s\n", msg);
                    session.commit();  // Only commit if message received and processed successfully
                }
            }
        } catch (JMSException e) {
            System.out.println("Exception caught: " + e);
            throw new RuntimeException(e);
        }
    }
}
