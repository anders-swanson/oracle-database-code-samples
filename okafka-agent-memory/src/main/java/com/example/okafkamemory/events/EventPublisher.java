package com.example.okafkamemory.events;

@FunctionalInterface
public interface EventPublisher {
    void publish(String topic, String key, Object event) throws Exception;
}
