package com.example.tracing.jdbc;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import oracle.jdbc.provider.opentelemetry.OpenTelemetryTraceEventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfiguration {
    private final Tracer tracer;

    public TracingConfiguration(Tracer tracer) {
        this.tracer = tracer;
    }

    @PostConstruct
    void init() {
        OpenTelemetryTraceEventListener tel = new OpenTelemetryTraceEventListener(tracer);
        OracleDatabaseTracingProvider.setTraceEventListener(tel);
    }
}
