package com.example.support;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.support.messaging.TicketEventConsumer;
import com.example.support.model.TicketOpenedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SupportTicketWorkflow {
    private final Properties okafkaProperties;
    private final KafkaConsumer<String, TicketOpenedEvent> consumer;
    private final TicketSearchService ticketSearchService;
    private final String topicName;
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "support-ticket-enricher");
        thread.setDaemon(true);
        return thread;
    });
    private TicketEventConsumer ticketEventConsumer;

    SupportTicketWorkflow(
            @Qualifier("okafkaProperties") Properties okafkaProperties,
            KafkaConsumer<String, TicketOpenedEvent> consumer,
            TicketSearchService ticketSearchService,
            @Value("${support.topic.ticket-opened}") String topicName
    ) {
        this.okafkaProperties = okafkaProperties;
        this.consumer = consumer;
        this.ticketSearchService = ticketSearchService;
        this.topicName = topicName;
    }

    @PostConstruct
    void start() {
        createTopic();
        ticketEventConsumer = new TicketEventConsumer(consumer, ticketSearchService, topicName);
        consumerExecutor.submit(ticketEventConsumer);
    }

    @PreDestroy
    void stop() throws InterruptedException {
        if (ticketEventConsumer != null) {
            ticketEventConsumer.close();
        }
        consumerExecutor.shutdown();
        consumerExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private void createTopic() {
        try (Admin admin = AdminClient.create(okafkaProperties)) {
            admin.createTopics(List.of(new NewTopic(topicName, 1, (short) 1))).all().get();
        } catch (Exception exception) {
            if (topicAlreadyExists(exception)) {
                return;
            }
            throw new IllegalStateException("Unable to create TxEventQ topic " + topicName, exception);
        }
    }

    private boolean topicAlreadyExists(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof TopicExistsException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
