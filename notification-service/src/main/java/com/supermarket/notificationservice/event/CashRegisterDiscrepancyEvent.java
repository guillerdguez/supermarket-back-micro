package com.supermarket.notificationservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashRegisterDiscrepancyEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long cashRegisterId,
        Long branchId,
        String branchName,
        BigDecimal variance) {
}
