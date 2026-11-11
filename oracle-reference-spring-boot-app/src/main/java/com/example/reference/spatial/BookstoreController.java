package com.example.reference.spatial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BookstoreController {
    private final BookstoreService stores;

    public BookstoreController(BookstoreService stores) {
        this.stores = stores;
    }

    public record StoreRequest(@NotBlank @Size(max = 120) String name,
                               @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
                               @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude) { }

    @PostMapping("/stores")
    public ResponseEntity<BookstoreService.Store> createStore(@RequestBody @Valid StoreRequest request) {
        var store = stores.create(request.name(), request.longitude(), request.latitude());
        return ResponseEntity.created(URI.create("/api/stores/" + store.id())).body(store);
    }

    @GetMapping("/stores/{id}")
    public ResponseEntity<BookstoreService.Store> getStore(@PathVariable @Positive long id) {
        return stores.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stores")
    public List<BookstoreService.NearbyStore> nearby(
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam(defaultValue = "10") @DecimalMin("0.1") @DecimalMax("100") double radiusKm) {
        return stores.nearby(longitude, latitude, radiusKm);
    }
}
