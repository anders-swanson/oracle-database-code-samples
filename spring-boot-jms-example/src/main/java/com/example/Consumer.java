package com.example;

import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class Consumer {
    private final CountDownLatch latch;

    public Consumer(@Value("${txeventq.consumer.numMessages:5}") int numMessages) {
        latch = new CountDownLatch(numMessages);
    }

    @JmsListener(destination = "${txeventq.queue.name:testqueue}", id = "sampleConsumer")
    public void receiveMessage(String message) {
        System.out.printf("Received message: %s%n", message);
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }
}
