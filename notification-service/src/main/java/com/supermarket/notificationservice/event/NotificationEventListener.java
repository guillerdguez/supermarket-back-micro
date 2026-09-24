package com.supermarket.notificationservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationEventHandler handler;

    @KafkaListener(topics = NotificationTopics.STOCK_LOW)
    public void onLowStock(StockLowEvent event) {
        handler.onLowStock(event);
    }

    @KafkaListener(topics = NotificationTopics.TRANSFER_REQUESTED)
    public void onTransferRequested(TransferEvent event) {
        handler.onTransferRequested(event);
    }

    @KafkaListener(topics = NotificationTopics.TRANSFER_APPROVED)
    public void onTransferApproved(TransferEvent event) {
        handler.onTransferApproved(event);
    }

    @KafkaListener(topics = NotificationTopics.TRANSFER_REJECTED)
    public void onTransferRejected(TransferEvent event) {
        handler.onTransferRejected(event);
    }

    @KafkaListener(topics = NotificationTopics.TRANSFER_COMPLETED)
    public void onTransferCompleted(TransferEvent event) {
        handler.onTransferCompleted(event);
    }

    @KafkaListener(topics = NotificationTopics.SALE_CANCELLED)
    public void onSaleCancelled(SaleCancelledEvent event) {
        handler.onSaleCancelled(event);
    }

    @KafkaListener(topics = NotificationTopics.CASH_REGISTER_DISCREPANCY)
    public void onCashRegisterDiscrepancy(CashRegisterDiscrepancyEvent event) {
        handler.onCashRegisterDiscrepancy(event);
    }
}
