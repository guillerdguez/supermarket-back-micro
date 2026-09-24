package com.supermarket.salesservice.event;

import com.supermarket.commons.event.DomainEvent;

import java.time.LocalDateTime;

public record SaleCancelledEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long saleId,
        Long branchId,
        String branchName,
        String reason,
        Long cancelledById,
        String cancelledByUsername) implements DomainEvent {

    @Override
    public String key() {
        return String.valueOf(branchId);
    }
}
