package com.example.tracing.jms;

import javax.sql.DataSource;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import oracle.jakarta.jms.AQjmsFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JMSTracingApp {
    public static void main(String[] args) {
        SpringApplication.run(JMSTracingApp.class, args);
    }

    @Bean
    public ConnectionFactory aqJmsConnectionFactory(DataSource ds) throws JMSException {
        return AQjmsFactory.getConnectionFactory(ds);
    }
}
