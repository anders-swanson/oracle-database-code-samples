package com.example.tracing.jms;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    private final CountDownLatch latch;

    public Consumer(@Value("${txeventq.consumer.numMessages:5}") int numMessages) {
        latch = new CountDownLatch(numMessages);
    }

    @JmsListener(destination = "${txeventq.queue.name:testqueue}", id = "sampleConsumer")
    public void receiveMessage(String message) {
        log.info("Received Message: {}", message);
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }
}
