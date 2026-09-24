package com.supermarket.salesservice.event;

import com.supermarket.commons.kafka.DomainEventPublisher;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.sale.Sale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SalesEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public void saleCompleted(Sale sale) {
        SaleCompletedEvent event = new SaleCompletedEvent(
                UUID.randomUUID().toString(),
                SalesTopics.SALE_COMPLETED,
                LocalDateTime.now(),
                sale.getId(),
                sale.getBranchId(),
                sale.getCashRegister() != null ? sale.getCashRegister().getId() : null,
                sale.getCreatedById(),
                sale.getDate(),
                sale.getTotal(),
                sale.getDetails().stream()
                        .map(detail -> new SaleCompletedEvent.Line(detail.getProductId(), detail.getQuantity(),
                                detail.getPrice()))
                        .toList());
        domainEventPublisher.publishAfterCommit(SalesTopics.SALE_COMPLETED, event);
    }

    public void saleCancelled(Sale sale) {
        SaleCancelledEvent event = new SaleCancelledEvent(
                UUID.randomUUID().toString(),
                SalesTopics.SALE_CANCELLED,
                LocalDateTime.now(),
                sale.getId(),
                sale.getBranchId(),
                sale.getBranchName(),
                sale.getCancellationReason(),
                sale.getCancelledById(),
                sale.getCancelledByUsername());
        domainEventPublisher.publishAfterCommit(SalesTopics.SALE_CANCELLED, event);
    }

    public void cashRegisterDiscrepancy(CashRegister register, BigDecimal variance) {
        CashRegisterDiscrepancyEvent event = new CashRegisterDiscrepancyEvent(
                UUID.randomUUID().toString(),
                SalesTopics.CASH_REGISTER_DISCREPANCY,
                LocalDateTime.now(),
                register.getId(),
                register.getBranchId(),
                register.getBranchName(),
                variance);
        domainEventPublisher.publishAfterCommit(SalesTopics.CASH_REGISTER_DISCREPANCY, event);
    }
}
