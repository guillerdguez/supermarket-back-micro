package com.supermarket.auditservice.event;

import java.time.LocalDateTime;

public record AuthActivityEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        String username,
        String action,
        String details,
        String ipAddress,
        String status) {
}
