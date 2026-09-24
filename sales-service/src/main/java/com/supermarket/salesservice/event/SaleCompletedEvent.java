package com.supermarket.salesservice.event;

import com.supermarket.commons.event.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SaleCompletedEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long saleId,
        Long branchId,
        Long cashRegisterId,
        Long createdById,
        LocalDate date,
        BigDecimal total,
        List<Line> lines) implements DomainEvent {

    public record Line(Long productId, Integer quantity, BigDecimal unitPrice) {
    }

    @Override
    public String key() {
        return String.valueOf(branchId);
    }
}
