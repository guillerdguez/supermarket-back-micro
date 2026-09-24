package com.supermarket.branchservice.event;

import com.supermarket.commons.event.DomainEvent;

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
        Integer minStock) implements DomainEvent {

    @Override
    public String key() {
        return branchId + "-" + productId;
    }
}
