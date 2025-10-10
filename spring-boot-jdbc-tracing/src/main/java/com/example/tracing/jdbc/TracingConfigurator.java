package com.example.tracing.jdbc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class TracingConfigurator implements BeanPostProcessor {
    private final OpenTelemetry openTelemetry;

    public TracingConfigurator(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            GlobalOpenTelemetry.set(openTelemetry);
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
