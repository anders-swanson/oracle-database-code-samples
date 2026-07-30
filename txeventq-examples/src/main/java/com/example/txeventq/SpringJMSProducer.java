package com.example.txeventq;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsClient;
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
        private final JmsClient jmsClient;

        public Producer(JmsClient jmsClient) {
            this.jmsClient = jmsClient;
        }

        @PostConstruct
        public void init() {
            prompt((s) ->
                    // Produce messages to a JMS queue
                    jmsClient.destination(JMS_QUEUE_NAME)
                            .send(s));
        }
    }
}
