package com.supermarket.transferservice.event;

import com.supermarket.commons.kafka.DomainEventPublisher;
import com.supermarket.transferservice.model.transfer.StockTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public void transferRequested(StockTransfer transfer) {
        publish(TransferTopics.REQUESTED, transfer);
    }

    public void transferApproved(StockTransfer transfer) {
        publish(TransferTopics.APPROVED, transfer);
    }

    public void transferRejected(StockTransfer transfer) {
        publish(TransferTopics.REJECTED, transfer);
    }

    public void transferCompleted(StockTransfer transfer) {
        publish(TransferTopics.COMPLETED, transfer);
    }

    private void publish(String topic, StockTransfer transfer) {
        TransferEvent event = new TransferEvent(
                UUID.randomUUID().toString(),
                topic,
                LocalDateTime.now(),
                transfer.getId(),
                transfer.getQuantity(),
                transfer.getProductId(),
                transfer.getProductName(),
                transfer.getSourceBranchId(),
                transfer.getSourceBranchName(),
                transfer.getTargetBranchId(),
                transfer.getTargetBranchName(),
                transfer.getRequestedById(),
                transfer.getRequestedByUsername(),
                transfer.getRejectionReason());
        domainEventPublisher.publishAfterCommit(topic, event);
    }
}
