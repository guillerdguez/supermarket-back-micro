package com.supermarket.catalogservice.event;

import com.supermarket.commons.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long productId,
        String name,
        String category,
        BigDecimal price) implements DomainEvent {

    @Override
    public String key() {
        return String.valueOf(productId);
    }
}
