package com.example.okafkamemory.intake;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.oracle.okafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Configuration
public class OkafkaIntakeConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OkafkaIntakeConfiguration.class);

    @Bean("intakeOkafkaProperties")
    Properties intakeOkafkaProperties(
            @Value("${memory.okafka.service-name}") String serviceName,
            @Value("${memory.okafka.security-protocol}") String securityProtocol,
            @Value("${memory.okafka.bootstrap-servers}") String bootstrapServers,
            @Value("${memory.okafka.tns-admin}") String tnsAdmin,
            @Value("${memory.okafka.tns-alias}") String tnsAlias) {
        Properties properties = new Properties();
        properties.put("oracle.service.name", serviceName);
        properties.put("security.protocol", securityProtocol);
        properties.put("oracle.net.tns_admin", tnsAdmin);
        if ("SSL".equalsIgnoreCase(securityProtocol)) {
            properties.put("tns.alias", tnsAlias);
        } else {
            properties.put("bootstrap.servers", bootstrapServers);
        }
        return properties;
    }

    public static void ensureTopic(Properties baseProperties, String topic) {
        if (baseProperties.getProperty("oracle.net.tns_admin", "").isBlank()) {
            throw new IllegalArgumentException("memory.okafka.tns-admin must point to ojdbc.properties");
        }
        Properties properties = new Properties();
        properties.putAll(baseProperties);
        try (Admin admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 0))).all().get();
        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof TopicExistsException) {
                System.out.println("[ADMIN] Topic already exists");
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
