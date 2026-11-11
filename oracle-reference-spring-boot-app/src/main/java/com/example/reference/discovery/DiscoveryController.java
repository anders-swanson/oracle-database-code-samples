package com.example.reference.discovery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DiscoveryController {
    private final DiscoveryService discovery;

    public DiscoveryController(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/discovery")
    public List<DiscoveryService.Match> discover(
            @RequestParam @Positive long seedBookId,
            @RequestParam @NotBlank @Size(max = 2000) String q,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam(defaultValue = "10") @DecimalMin("0.1") @DecimalMax("100") double radiusKm,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return discovery.discover(seedBookId, q, longitude, latitude, radiusKm, limit);
    }
}
