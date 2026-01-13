package com.example.propagation;

import java.util.UUID;
import java.util.Scanner;

import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import javax.sql.DataSource;
import oracle.jakarta.jms.AQjmsFactory;
import oracle.jakarta.jms.AQjmsSession;
import oracle.jakarta.jms.AQjmsTopicPublisher;

public class JMSProducer implements Runnable {
    private final DataSource dataSource;
    private final String username;
    private final String topicName;

    public static void main(String[] args) {
        // Connect to the source database
        final String username = "sourceuser";
        DataSource ds = DataSourceFactory.create(username, 1522);
        new JMSProducer(ds, username, "source").run();
    }

    public JMSProducer(DataSource dataSource, String username, String topicName) {
        this.dataSource = dataSource;
        this.username = username;
        this.topicName = topicName;
    }


    @Override
    public void run() {
        // Create a new JMS connection and session.
        try (TopicConnection connection = AQjmsFactory.getTopicConnectionFactory(dataSource).createTopicConnection();
             AQjmsSession session = (AQjmsSession) connection.createTopicSession(true, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            Topic jmsTopic = session.getTopic(username, topicName);
            // The JMS Connection must be started before use.
            AQjmsTopicPublisher publisher = (AQjmsTopicPublisher) session.createPublisher(jmsTopic);
            // Read messages from console and send to the topic.
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("Enter message (or 'exit' to quit): ");
                String s = scanner.nextLine();
                if (s.equalsIgnoreCase("exit")) {
                    break;
                }
                TextMessage message = session.createTextMessage(s);
                message.setJMSCorrelationID(UUID.randomUUID().toString());
                publisher.publish(message);
                session.commit();
            }
        } catch (JMSException e) {
            System.out.println("JMSException caught: " + e);
            throw new RuntimeException(e);
        }

        System.out.println("[PRODUCER] Closing producer!");
    }
}
