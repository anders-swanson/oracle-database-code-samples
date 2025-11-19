package com.example.configclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    @Value("${config.key}")
    private String configServerValue;

    @GetMapping("/value")
    public String getConfigValue() {
        return String.format("This is my config value! %s\n", configServerValue);
    }
}
