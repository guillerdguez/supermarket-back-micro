package com.supermarket.salesservice.event;

import com.supermarket.commons.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashRegisterDiscrepancyEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long cashRegisterId,
        Long branchId,
        String branchName,
        BigDecimal variance) implements DomainEvent {

    @Override
    public String key() {
        return String.valueOf(branchId);
    }
}
