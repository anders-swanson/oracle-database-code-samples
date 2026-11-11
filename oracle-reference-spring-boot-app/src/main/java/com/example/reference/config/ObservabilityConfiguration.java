package com.example.reference.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {
    @Bean
    static AbstractDependsOnBeanFactoryPostProcessor telemetryBeforeDataSource() {
        // SQL initialization and JPA can open connections during startup.
        return new AbstractDependsOnBeanFactoryPostProcessor(DataSource.class, "telemetry") { };
    }

    @Bean
    InitializingBean telemetry(OpenTelemetry openTelemetry) {
        return () -> {
            // Oracle's JDBC provider uses the global API; Boot owns the SDK and exporters.
            GlobalOpenTelemetry.set(openTelemetry);
            OpenTelemetryAppender.install(openTelemetry);
        };
    }
}
