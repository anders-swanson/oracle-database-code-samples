package com.example.jms.queue;

import java.util.List;

import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.QueueConnection;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import javax.sql.DataSource;
import oracle.jakarta.jms.AQjmsFactory;
import oracle.jakarta.jms.AQjmsQueueSender;
import oracle.jakarta.jms.AQjmsSession;

public class QueueProducer implements Runnable {
    private final DataSource dataSource;
    private final String username;
    private final String queueName;
    private final List<String> input;


    public QueueProducer(DataSource dataSource, String username, String queueName, List<String> input) {
        this.dataSource = dataSource;
        this.username = username;
        this.queueName = queueName;
        this.input = input;
    }


    @Override
    public void run() {
        System.out.printf("[PRODUCER] Producing %d messages.\n", input.size());
        // Create a new JMS connection and session.
        try (QueueConnection connection = AQjmsFactory.getQueueConnectionFactory(dataSource).createQueueConnection();
             AQjmsSession session = (AQjmsSession) connection.createQueueSession(true, Session.AUTO_ACKNOWLEDGE)) {

            // The JMS Connection must be started before use.
            connection.start();
            Queue jmsQueue = session.getQueue(username, queueName);
            AQjmsQueueSender sender = (AQjmsQueueSender) session.createSender(jmsQueue);
            // Write the input data as JMS text messages to a JMS topic.
            for (String s : input) {
                TextMessage message = session.createTextMessage(s);
                sender.send(message);
            }
            session.commit();
        } catch (JMSException e) {
            System.out.println("JMSException caught: " + e);
            throw new RuntimeException(e);
        }

        System.out.println("[PRODUCER] Sent all JMS messages. Closing producer!");
    }
}
