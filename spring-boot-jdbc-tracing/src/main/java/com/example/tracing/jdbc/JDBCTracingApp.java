package com.example.tracing.jdbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JDBCTracingApp {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(JDBCTracingApp.class, args);
    }
}
