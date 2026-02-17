package com.example.jdv;

import com.oracle.spring.json.duality.annotation.JsonRelationalDualityViewScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.event.EventListener;

@SpringBootApplication(scanBasePackages = {
        "com.example.jdv",
        // Enable the duality view event listener
        "com.oracle.spring.json.duality.builder"
})
@EntityScan(basePackages = {"com.example.jdv.movie"})
@JsonRelationalDualityViewScan(basePackages = {"com.example.jdv.movie"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
