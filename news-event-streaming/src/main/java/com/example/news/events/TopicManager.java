package com.example.news.events;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.oracle.okafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TopicManager {
    private final Admin admin;
    private final String rawTopic;
    private final String parsedTopic;

    public TopicManager(Admin admin,
                        @Value("${news.topic.raw}") String rawTopic,
                        @Value("${news.topic.parsed}") String parsedTopic) {
        this.admin = admin;
        this.rawTopic = rawTopic;
        this.parsedTopic = parsedTopic;
    }

    @PostConstruct
    public void init() {
        createTopicIfNotExists(admin, new NewTopic(rawTopic, 1, (short) 1));
        createTopicIfNotExists(admin, new NewTopic(parsedTopic, 1, (short) 1));
    }

    private void createTopicIfNotExists(Admin admin, NewTopic newTopic) {
        try {
            admin.createTopics(Collections.singletonList(newTopic))
                    .all()
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Topic {} already exists, skipping creation", newTopic);
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
