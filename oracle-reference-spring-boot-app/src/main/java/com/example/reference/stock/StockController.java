package com.example.reference.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/stores/{storeId}/books/{bookId}")
public class StockController {
    private final StockService stocks;

    public StockController(StockService stocks) {
        this.stocks = stocks;
    }

    public record NewStock(@NotNull @Min(0) @Max(1_000_000) Integer quantity) { }
    public record Adjustment(@Min(-1_000_000) @Max(1_000_000) int delta,
                             @NotNull @PositiveOrZero Long expectedVersion) { }

    @GetMapping
    public StockService.StockView get(@PathVariable @Positive long storeId, @PathVariable @Positive long bookId) {
        return stocks.get(storeId, bookId);
    }

    @PostMapping
    public ResponseEntity<StockService.StockView> create(@PathVariable @Positive long storeId,
            @PathVariable @Positive long bookId, @RequestBody @Valid NewStock request) {
        var stock = stocks.create(storeId, bookId, request.quantity());
        return ResponseEntity.created(URI.create("/api/stores/" + storeId + "/books/" + bookId)).body(stock);
    }

    @PostMapping("/stock-adjustments")
    public StockService.StockView adjust(@PathVariable @Positive long storeId, @PathVariable @Positive long bookId,
                                         @RequestBody @Valid Adjustment request) {
        return stocks.adjust(storeId, bookId, request.delta(), request.expectedVersion());
    }

    @GetMapping("/stock-adjustments")
    public List<StockService.AdjustmentView> history(@PathVariable @Positive long storeId,
            @PathVariable @Positive long bookId, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return stocks.history(storeId, bookId, limit);
    }
}
