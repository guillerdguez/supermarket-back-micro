package com.supermarket.authservice.event;

import com.supermarket.commons.event.DomainEvent;

import java.time.LocalDateTime;

public record AuthActivityEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        String username,
        String action,
        String details,
        String ipAddress,
        String status) implements DomainEvent {

    @Override
    public String key() {
        return username;
    }
}
