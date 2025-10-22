package com.example.tracing.jdbc.custom;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
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
public class TracingConfiguration implements BeanPostProcessor {
    private final OpenTelemetry openTelemetry;
    private final TracingProperties tracingProperties;

    @Value("${spring.application.name}")
    private String appName;

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
        InetAddress address;

        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        if(bean instanceof DataSource ds) {
            Properties props = new Properties();
            String clientId = "%s@%s".formatted(appName, address.getHostName());
            props.setProperty("OCSID.CLIENTID", clientId);
            return new ClientInfoDataSource(ds, props);

        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
