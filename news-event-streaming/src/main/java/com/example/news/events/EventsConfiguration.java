package com.example.news.events;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.Admin;
import org.oracle.okafka.clients.admin.AdminClient;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventsConfiguration {
    @Value("${okafka.ojdbcPath}")
    private String ojdbcPath;

    @Value("${okafka.bootstrapServers:localhost:1521}")
    private String bootstrapServers;

    // We use the default 23ai Free service name
    @Value("${okafka.serviceName:freepdb1}")
    private String serviceName;

    // We use plaintext for a containerized, local database.
    // Use "SSL" for wallet connections, like Autonomous Database.
    @Value("${okafka.securityProtocol:PLAINTEXT}")
    private String securityProtocol;

    @Bean
    @Qualifier("stringProducer")
    public KafkaProducer<String, String> stringProducer() {
        Properties props = okafkaProperties();
        props.put("enable.idempotence", "true");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // This property is required for transactional producers
        props.put("oracle.transactional.producer", "true");

        // Note the use of the org.oracle.okafka.clients.producer.KafkaProducer class
        // for producing records to Oracle Database Transactional Event Queues.
        KafkaProducer<String, String> stringProducer = new KafkaProducer<>(props);
        // Initialize the producer for database transactions.
        // Done once during producer creation.
        stringProducer.initTransactions();
        return stringProducer;
    }

    @Bean
    @Qualifier("okafkaProperties")
    Properties okafkaProperties() {
        Properties props = new Properties();
        props.put("oracle.service.name", serviceName);
        props.put("security.protocol", securityProtocol);
        props.put("bootstrap.servers", bootstrapServers);
        // If using Oracle Database wallet, pass wallet directory
        props.put("oracle.net.tns_admin", ojdbcPath);
        return props;
    }

    @Bean
    Admin admin() {
        return AdminClient.create(okafkaProperties());
    }

}
