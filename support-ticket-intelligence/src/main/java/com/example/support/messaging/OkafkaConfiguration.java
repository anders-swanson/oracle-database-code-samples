package com.example.support.messaging;

import java.io.File;
import java.util.Properties;

import com.example.support.model.TicketOpenedEvent;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.consumer.KafkaConsumer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class OkafkaConfiguration {
    private final OSONKafkaSerializationFactory serializationFactory;

    public OkafkaConfiguration(OSONKafkaSerializationFactory serializationFactory) {
        this.serializationFactory = serializationFactory;
    }

    @Bean
    @Qualifier("okafkaProperties")
    public Properties okafkaProperties(
            @Value("${support.okafka.bootstrap-servers}") String bootstrapServers,
            @Value("${support.okafka.service-name}") String serviceName,
            @Value("${support.okafka.security-protocol}") String securityProtocol,
            @Value("${support.okafka.tns-admin:}") String tnsAdmin
    ) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("oracle.service.name", serviceName);
        properties.put("security.protocol", securityProtocol);
        properties.put("oracle.net.tns_admin", resolveTnsAdmin(tnsAdmin));
        return properties;
    }

    @Bean(destroyMethod = "close")
    public KafkaProducer<String, TicketOpenedEvent> ticketProducer(
            @Qualifier("okafkaProperties") Properties okafkaProperties
    ) {
        Properties properties = new Properties();
        properties.putAll(okafkaProperties);
        properties.put("enable.idempotence", "true");
        properties.put("oracle.transactional.producer", "true");
        properties.put("key.serializer", StringSerializer.class.getName());
        return new KafkaProducer<>(properties, new StringSerializer(), serializationFactory.createSerializer());
    }

    @Bean(destroyMethod = "")
    public KafkaConsumer<String, TicketOpenedEvent> ticketConsumer(
            @Qualifier("okafkaProperties") Properties okafkaProperties
    ) {
        Properties properties = new Properties();
        properties.putAll(okafkaProperties);
        properties.put("group.id", "SUPPORTTICKETENRICHER");
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        properties.put("max.poll.records", "10");
        return new KafkaConsumer<>(properties, new StringDeserializer(), serializationFactory.createDeserializer(TicketOpenedEvent.class));
    }

    private String resolveTnsAdmin(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath;
        }
        try {
            return new File(new ClassPathResource("").getURL().toURI()).getAbsolutePath();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve the directory containing ojdbc.properties", exception);
        }
    }
}
