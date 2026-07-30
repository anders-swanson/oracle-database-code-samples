package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsClient;
import org.springframework.stereotype.Component;

@Component
public class Producer {
    private final JmsClient jmsClient;
    private final String queueName;

    public Producer(JmsClient jmsClient,
                    @Value("${txeventq.queue.name:testqueue}") String queueName) {
        this.jmsClient = jmsClient;
        this.queueName = queueName;
    }

    public void enqueue(String message) {
        jmsClient.destination(queueName)
                .send(message);
    }
}
