package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Producer {
    private final JmsTemplate jmsTemplate;
    private final String queueName;

    public Producer(JmsTemplate jmsTemplate,
                    @Value("${txeventq.queue.name:testqueue}") String queueName) {
        this.jmsTemplate = jmsTemplate;
        this.queueName = queueName;
    }

    public void enqueue(String message) {
        jmsTemplate.convertAndSend(queueName, message);
    }
}
