package com.supermarket.notificationservice.event;

import java.time.LocalDateTime;

public record TransferEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long transferId,
        Integer quantity,
        Long productId,
        String productName,
        Long sourceBranchId,
        String sourceBranchName,
        Long targetBranchId,
        String targetBranchName,
        Long requestedById,
        String requestedByUsername,
        String rejectionReason) {
}
