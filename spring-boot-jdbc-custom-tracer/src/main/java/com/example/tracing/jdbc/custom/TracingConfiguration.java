package com.example.tracing.jdbc.custom;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(TracingProperties.class)
public class TracingConfiguration implements BeanPostProcessor {
    private final OpenTelemetry openTelemetry;
    private final TracingProperties tracingProperties;

    public TracingConfiguration(OpenTelemetry openTelemetry, TracingProperties tracingProperties) {
        this.openTelemetry = openTelemetry;
        this.tracingProperties = tracingProperties;
    }

    @PostConstruct
    void init() {
        // Configure the tracer
        Tracer tracer = openTelemetry.getTracer(
                JDBCTraceEventListener.class.getName()
        );
        JDBCTraceEventListenerProvider.setTracer(tracer);
        JDBCTraceEventListenerProvider.setTracingProperties(tracingProperties);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof DataSource ds) {
            return new ClientInfoDataSource(ds, tracingProperties.getClientInfo());
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
