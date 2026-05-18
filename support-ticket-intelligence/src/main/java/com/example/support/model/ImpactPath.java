package com.example.support.model;

public record ImpactPath(
        String customerName,
        String customerTier,
        long orderId,
        String orderStatus,
        String productName
) {
}
