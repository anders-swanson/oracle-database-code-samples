package com.example.okafkamemory;

import com.example.okafkamemory.intake.IncomingEvent;
import com.oracle.spring.json.jsonb.JSONB;
import com.oracle.spring.json.kafka.OSONKafkaSerializationFactory;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oracle.okafka.clients.producer.KafkaProducer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Send one demo fact through the same OKafka topic consumed by the application. */
public final class DemoProducer {
    private DemoProducer() {
    }

    public static void main(String[] args) throws Exception {
        String fact = args.length == 0 ? "I prefer dark mode" : String.join(" ", args).trim();
        if (fact.isBlank()) throw new IllegalArgumentException("A fact is required");
        // The producer publishes only; the running application consumes and processes memories.
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MemoryApplication.class)
                .web(WebApplicationType.NONE)
                .properties("memory.background-processing.enabled=false", "logging.level.root=ERROR",
                        "spring.main.banner-mode=off", "spring.main.log-startup-info=false")
                .run()) {
            Properties base = context.getBean("intakeOkafkaProperties", Properties.class);
            String topic = context.getEnvironment().getRequiredProperty("memory.intake.topic");
            System.out.println("Published source event: " + publish(base, topic, fact));
        }
    }

    public static UUID publish(Properties base, String topic, String fact) throws Exception {
        String message = fact.startsWith("remember ") ? fact : "remember " + fact;
        UUID sourceEventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("message", message);
        Properties producerProperties = new Properties();
        producerProperties.putAll(base);
        producerProperties.put("enable.idempotence", "true");
        IncomingEvent event = new IncomingEvent(sourceEventId, "user:demo", payload, true);
        try (var producer = new KafkaProducer<String, IncomingEvent>(producerProperties,
                new StringSerializer(),
                new OSONKafkaSerializationFactory(JSONB.createDefault()).createSerializer())) {
            producer.send(new ProducerRecord<>(topic, sourceEventId.toString(), event)).get();
        }
        return sourceEventId;
    }
}
