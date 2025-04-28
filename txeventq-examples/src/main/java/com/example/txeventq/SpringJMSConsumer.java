package com.example.txeventq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import static com.example.txeventq.Values.JMS_QUEUE_NAME;

@SpringBootApplication
// run with spring.profiles.active=jms-consumer
public class SpringJMSConsumer {
    public static void main(String[] args) {
        SpringApplication.run(SpringJMSConsumer.class, args);
    }

    @Component
    @Profile("jms-consumer")
    public static class Consumer {
        // Asynchronously receive messages from a given JMS queue
        @JmsListener(destination = JMS_QUEUE_NAME)
        public void receive(String message) {
            System.out.println("received message: " + message);
        }
    }
}
