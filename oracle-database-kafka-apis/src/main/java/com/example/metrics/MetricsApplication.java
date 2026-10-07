package com.example.metrics;

import java.util.Map;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(proxyBeanMethods = false)
public class MetricsApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(MetricsApplication.class);
        application.setDefaultProperties(Map.of("okafka.tns-admin", "src/test/resources"));
        application.run(args);
    }

    // Spring Boot binds these client metrics to the same registry as Actuator metrics.
    @Bean(destroyMethod = "close")
    KafkaClientMetrics producerMetrics(Producer<String, String> metricsProducer) {
        return new KafkaClientMetrics(metricsProducer);
    }

    @Bean(destroyMethod = "close")
    KafkaClientMetrics consumerMetrics(Consumer<String, String> metricsConsumer) {
        return new KafkaClientMetrics(metricsConsumer);
    }

    // Connect Logback to the OpenTelemetry instance and exporters managed by Spring Boot.
    @Bean
    InitializingBean openTelemetryAppenderInitializer(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
