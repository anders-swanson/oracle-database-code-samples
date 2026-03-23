package com.example.tracing.jdbc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class TracingConfigurator implements InitializingBean {
    private final OpenTelemetry openTelemetry;

    public TracingConfigurator(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        GlobalOpenTelemetry.set(openTelemetry);
    }
}
