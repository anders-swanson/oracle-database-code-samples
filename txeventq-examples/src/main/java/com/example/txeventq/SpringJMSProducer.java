package com.example.txeventq;

import jakarta.annotation.PostConstruct;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import javax.sql.DataSource;
import oracle.jakarta.jms.AQjmsFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import static com.example.txeventq.Prompt.prompt;
import static com.example.txeventq.Values.JMS_QUEUE_NAME;

@SpringBootApplication
// run with spring.profiles.active=jms-producer
public class SpringJMSProducer {
    public static void main(String[] args) {
        SpringApplication.run(SpringJMSProducer.class, args);
    }

    @Component
    @Profile("jms-producer")
    public static class Producer {
        private final JmsTemplate jmsTemplate;

        public Producer(JmsTemplate jmsTemplate) {
            this.jmsTemplate = jmsTemplate;
        }

        @PostConstruct
        public void init() {
            prompt((s) ->
                    // Produce messages to a JMS queue
                    jmsTemplate.convertAndSend(JMS_QUEUE_NAME, s)
            );
        }
    }
}
