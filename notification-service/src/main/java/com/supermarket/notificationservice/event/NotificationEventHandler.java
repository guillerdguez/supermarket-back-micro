package com.supermarket.notificationservice.event;

import com.supermarket.notificationservice.client.RecipientDirectory;
import com.supermarket.notificationservice.model.notification.NotificationType;
import com.supermarket.notificationservice.model.notification.Recipient;
import com.supermarket.notificationservice.model.notification.ReferenceType;
import com.supermarket.notificationservice.model.processed.ProcessedEvent;
import com.supermarket.notificationservice.model.processed.ProcessedEventRepository;
import com.supermarket.notificationservice.service.business.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventHandler {

    private final NotificationService notificationService;
    private final RecipientDirectory recipientDirectory;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void onLowStock(StockLowEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Low stock alert: '%s' in branch '%s'. Current: %d, Minimum: %d",
                event.productName(), event.branchName(), event.currentStock(), event.minStock());
        notificationService.createNotificationForUsers(
                recipientDirectory.managers(), NotificationType.LOW_STOCK, message, null, null, null);
    }

    @Transactional
    public void onTransferRequested(TransferEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Transfer requested: %d units of '%s' from '%s' to '%s'",
                event.quantity(), event.productName(), event.sourceBranchName(), event.targetBranchName());
        notificationService.createNotificationForUsers(recipientDirectory.managers(),
                NotificationType.TRANSFER_REQUESTED, message, null, ReferenceType.TRANSFER, event.transferId());
    }

    @Transactional
    public void onTransferApproved(TransferEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Your transfer request for %d units of '%s' to '%s' has been approved",
                event.quantity(), event.productName(), event.targetBranchName());
        notificationService.createNotification(requester(event), NotificationType.TRANSFER_APPROVED, message,
                null, ReferenceType.TRANSFER, event.transferId());
    }

    @Transactional
    public void onTransferRejected(TransferEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Your transfer request for %d units of '%s' to '%s' has been rejected. Reason: %s",
                event.quantity(), event.productName(), event.targetBranchName(), event.rejectionReason());
        notificationService.createNotification(requester(event), NotificationType.TRANSFER_REJECTED, message,
                null, ReferenceType.TRANSFER, event.transferId());
    }

    @Transactional
    public void onTransferCompleted(TransferEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Transfer completed: %d units of '%s' moved from '%s' to '%s'",
                event.quantity(), event.productName(), event.sourceBranchName(), event.targetBranchName());
        notificationService.createNotificationForUsers(recipientDirectory.managers(),
                NotificationType.TRANSFER_COMPLETED, message, null, ReferenceType.TRANSFER, event.transferId());
    }

    @Transactional
    public void onCashRegisterDiscrepancy(CashRegisterDiscrepancyEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Cash register discrepancy detected in branch '%s'. Variance: %.2f",
                event.branchName(), event.variance());
        notificationService.createNotificationForUsers(recipientDirectory.managers(),
                NotificationType.CASH_REGISTER_DISCREPANCY, message, null, null, null);
    }

    @Transactional
    public void onSaleCancelled(SaleCancelledEvent event) {
        if (alreadyProcessed(event.eventId(), event.eventType())) {
            return;
        }
        String message = String.format(
                "Sale #%d in branch '%s' has been cancelled. Reason: %s",
                event.saleId(), event.branchName(), event.reason());
        notificationService.createNotificationForUsers(recipientDirectory.managers(),
                NotificationType.SALE_CANCELLED, message, null, ReferenceType.SALE, event.saleId());
    }

    private Recipient requester(TransferEvent event) {
        return new Recipient(event.requestedById(), event.requestedByUsername());
    }

    private boolean alreadyProcessed(String eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicated event {} [{}]", eventType, eventId);
            return true;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, LocalDateTime.now()));
        return false;
    }
}
