package com.example.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(proxyBeanMethods = false)
public class ObservabilityApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }

    @Bean
    InitializingBean telemetry(OpenTelemetry openTelemetry) {
        return () -> {
            // Oracle's provider uses the global API; Boot still owns the SDK and exporters.
            GlobalOpenTelemetry.set(openTelemetry);
            OpenTelemetryAppender.install(openTelemetry);
        };
    }
}
