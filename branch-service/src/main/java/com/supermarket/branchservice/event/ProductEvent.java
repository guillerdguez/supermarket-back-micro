package com.supermarket.branchservice.event;

import java.time.LocalDateTime;

public record ProductEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long productId,
        String name) {
}
