package com.supermarket.notificationservice.event;

import java.time.LocalDateTime;

public record SaleCancelledEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long saleId,
        Long branchId,
        String branchName,
        String reason) {
}
