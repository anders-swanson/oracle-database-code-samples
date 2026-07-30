package com.example.tracing.jms;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ScheduledProducer {
    private final JmsClient producer;
    private final String queueName;

    public ScheduledProducer(JmsClient producer,
                             @Value("${txeventq.queue.name:testqueue}") String queueName) {
        this.producer = producer;
        this.queueName = queueName;
    }

    @Scheduled(fixedRate = 1000)
    public void produce() {
        this.producer.destination(queueName)
                .send("Message: " + Instant.now());
    }
}
