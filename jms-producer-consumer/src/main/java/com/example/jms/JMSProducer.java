package com.example.jms;

import java.util.List;
import java.util.UUID;

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
    private final List<String> input;


    public JMSProducer(DataSource dataSource, String username, String topicName, List<String> input) {
        this.dataSource = dataSource;
        this.username = username;
        this.topicName = topicName;
        this.input = input;
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
            // Write the input data as JMS text messages to a JMS topic.
            for (String s : input) {
                TextMessage message = session.createTextMessage(s);
                message.setJMSCorrelationID(UUID.randomUUID().toString());
                publisher.publish(message);
            }
            session.commit();
        } catch (JMSException e) {
            System.out.println("JMSException caught: " + e);
            throw new RuntimeException(e);
        }

        System.out.println("[PRODUCER] Sent all JMS messages. Closing producer!");
    }
}
