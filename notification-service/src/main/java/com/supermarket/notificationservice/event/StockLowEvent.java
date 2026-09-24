package com.supermarket.notificationservice.event;

import java.time.LocalDateTime;

public record StockLowEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long branchId,
        String branchName,
        Long productId,
        String productName,
        Integer currentStock,
        Integer minStock) {
}
