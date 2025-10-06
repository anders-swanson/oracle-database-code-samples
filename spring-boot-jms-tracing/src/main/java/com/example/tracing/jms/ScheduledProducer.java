package com.example.tracing.jms;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ScheduledProducer {
    private final JmsTemplate producer;
    private final String queueName;

    public ScheduledProducer(JmsTemplate producer,
                             @Value("${txeventq.queue.name:testqueue}") String queueName) {
        this.producer = producer;
        this.queueName = queueName;
    }

    @Scheduled(fixedRate = 1000)
    public void produce() {
        this.producer.convertAndSend(queueName, "Message: " + Instant.now());
    }
}
