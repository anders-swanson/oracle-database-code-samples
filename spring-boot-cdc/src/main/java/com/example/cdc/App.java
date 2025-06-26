package com.example.cdc;

import javax.sql.DataSource;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import oracle.jakarta.jms.AQjmsFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    public ConnectionFactory aqJmsConnectionFactory(DataSource ds) throws JMSException {
        return AQjmsFactory.getConnectionFactory(ds);
    }
}
