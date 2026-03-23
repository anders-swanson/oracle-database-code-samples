package com.example.tracing.jdbc.custom;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(TracingProperties.class)
public class TracingConfiguration implements InitializingBean {
    private final OpenTelemetry openTelemetry;
    private final TracingProperties tracingProperties;

    public TracingConfiguration(OpenTelemetry openTelemetry, TracingProperties tracingProperties) {
        this.openTelemetry = openTelemetry;
        this.tracingProperties = tracingProperties;
    }

    @Override
    public void afterPropertiesSet() {
        GlobalOpenTelemetry.set(openTelemetry);

        // Configure the tracer
        Tracer tracer = openTelemetry.getTracer(
                JDBCTraceEventListener.class.getName()
        );
        JDBCTraceEventListenerProvider.setTracer(tracer);
        JDBCTraceEventListenerProvider.setTracingProperties(tracingProperties);
    }
}
