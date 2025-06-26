package com.example.cdc;

import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class CDCConsumer {
    private final CountDownLatch countDownLatch;

    public CDCConsumer(@Value("${expected.messages:10}") int numMessages) {
        countDownLatch = new CountDownLatch(numMessages);
    }
    @JmsListener(destination = "${txeventq.queue.name:ticket_event}", id = "cdcConsumer")
    public void receiveMessage(String message) {
        System.out.printf("[CONSUMER] Processing ticket: %s%n", message);
        countDownLatch.countDown();
    }

    public void await() throws InterruptedException {
        countDownLatch.await();
    }
}
