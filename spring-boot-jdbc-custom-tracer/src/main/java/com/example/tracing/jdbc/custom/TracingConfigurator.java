package com.example.tracing.jdbc.custom;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TracingProperties.class)
public class TracingConfigurator {
    private final OpenTelemetry openTelemetry;
    private final TracingProperties tracingProperties;

    public TracingConfigurator(OpenTelemetry openTelemetry, TracingProperties tracingProperties, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracingProperties = tracingProperties;
    }

    @PostConstruct
    void init() {
        Tracer tracer = openTelemetry.getTracer(
                JDBCTraceEventListener.class.getName()
        );
        JDBCTraceEventListenerProvider.setTracer(tracer);
        JDBCTraceEventListenerProvider.setTracingProperties(tracingProperties);
    }

}
