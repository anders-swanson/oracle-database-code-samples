package com.example.support.messaging;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.support.TicketSearchService;
import com.example.support.model.TicketOpenedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TicketEventConsumer implements Runnable, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TicketEventConsumer.class);

    private final KafkaConsumer<String, TicketOpenedEvent> consumer;
    private final TicketSearchService ticketSearchService;
    private final String topicName;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public TicketEventConsumer(
            KafkaConsumer<String, TicketOpenedEvent> consumer,
            TicketSearchService ticketSearchService,
            String topicName
    ) {
        this.consumer = consumer;
        this.ticketSearchService = ticketSearchService;
        this.topicName = topicName;
    }

    @Override
    public void run() {
        try {
            consumer.subscribe(List.of(topicName));
            while (running.get()) {
                try {
                    ConsumerRecords<String, TicketOpenedEvent> records = consumer.poll(Duration.ofMillis(250));
                    if (records.isEmpty()) {
                        continue;
                    }
                    for (ConsumerRecord<String, TicketOpenedEvent> event : records) {
                        ticketSearchService.enrichTicket(consumer.getDBConnection(), event.value().ticketId());
                    }
                    consumer.commitSync();
                } catch (WakeupException exception) {
                    if (running.get()) {
                        throw exception;
                    }
                } catch (Exception exception) {
                    log.error("Unable to enrich support ticket event batch", exception);
                    rollbackConsumerConnection();
                }
            }
        } finally {
            consumer.close();
        }
    }

    private void rollbackConsumerConnection() {
        try {
            consumer.getDBConnection().rollback();
        } catch (SQLException exception) {
            log.error("Unable to roll back support ticket consumer transaction", exception);
        }
    }

    @Override
    public void close() {
        running.set(false);
    }
}
