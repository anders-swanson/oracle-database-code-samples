package com.example.metrics;

import java.util.Properties;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsClientConfiguration {
    @Bean
    Properties okafkaConnectionProperties(@Value("${okafka.bootstrap-servers:localhost:1521}") String servers,
                                          @Value("${okafka.service-name:freepdb1}") String service,
                                          @Value("${okafka.tns-admin}") String tnsAdmin) {
        var properties = new Properties();
        properties.put("bootstrap.servers", servers);
        properties.put("oracle.service.name", service);
        properties.put("oracle.net.tns_admin", tnsAdmin);
        properties.put("security.protocol", "PLAINTEXT");
        return properties;
    }

    @Bean(initMethod = "initTransactions", destroyMethod = "close")
    KafkaProducer<String, String> metricsProducer(Properties okafkaConnectionProperties) {
        var properties = new Properties();
        properties.putAll(okafkaConnectionProperties);
        properties.put("client.id", "metrics-producer");
        properties.put("enable.idempotence", "true");
        properties.put("oracle.transactional.producer", "true");
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    // The polling application thread closes the consumer after it stops.
    @Bean(destroyMethod = "")
    KafkaConsumer<String, String> metricsConsumer(Properties okafkaConnectionProperties) {
        var properties = new Properties();
        properties.putAll(okafkaConnectionProperties);
        properties.put("client.id", "metrics-consumer");
        properties.put("group.id", "METRICS_SAMPLE_GROUP");
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }
}
