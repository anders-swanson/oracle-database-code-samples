package com.example.support.model;

public record TicketRequest(
        long customerId,
        long orderId,
        long productId,
        String subject,
        String body,
        String errorCode,
        String severity,
        String slaStatus
) {
}
