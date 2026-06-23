package com.example.ucp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration")
public class UcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(UcpApplication.class, args);
    }
}
