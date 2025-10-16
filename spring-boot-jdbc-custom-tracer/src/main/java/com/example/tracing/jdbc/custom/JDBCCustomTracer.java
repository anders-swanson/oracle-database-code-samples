package com.example.tracing.jdbc.custom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JDBCCustomTracer {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(JDBCCustomTracer.class, args);
    }
}
