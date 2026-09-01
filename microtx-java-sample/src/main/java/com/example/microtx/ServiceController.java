package com.example.microtx;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ServiceController {

    @GetMapping("/")
    ServiceStatus status() {
        return new ServiceStatus("microtx-java-sample", "ready");
    }

    record ServiceStatus(String service, String status) {
    }
}
