package com.example;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.oracle.okafka.clients.admin.AdminClient;

public class AdminUtil {
    public static void createTopicIfNotExists(Properties okafkaProperties,
                                              NewTopic newTopic) {
        try (Admin admin = AdminClient.create(okafkaProperties)) {
            admin.createTopics(Collections.singletonList(newTopic))
                    .all()
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                System.out.println("Topic already exists, skipping creation");
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
