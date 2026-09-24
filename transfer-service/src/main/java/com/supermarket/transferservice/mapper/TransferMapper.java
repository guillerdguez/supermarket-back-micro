package com.supermarket.transferservice.mapper;

import com.supermarket.transferservice.dto.transfer.TransferResponse;
import com.supermarket.transferservice.model.transfer.StockTransfer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransferMapper {

    public TransferResponse toResponse(StockTransfer transfer) {
        if (transfer == null) return null;

        return TransferResponse.builder()
                .id(transfer.getId())
                .quantity(transfer.getQuantity())
                .status(transfer.getStatus())
                .requestedAt(transfer.getRequestedAt())
                .approvedAt(transfer.getApprovedAt())
                .completedAt(transfer.getCompletedAt())
                .rejectionReason(transfer.getRejectionReason())
                .sourceBranchId(transfer.getSourceBranchId())
                .sourceBranchName(transfer.getSourceBranchName())
                .targetBranchId(transfer.getTargetBranchId())
                .targetBranchName(transfer.getTargetBranchName())
                .productId(transfer.getProductId())
                .productName(transfer.getProductName())
                .requestedById(transfer.getRequestedById())
                .requestedByUsername(transfer.getRequestedByUsername())
                .approvedById(transfer.getApprovedById())
                .approvedByUsername(transfer.getApprovedByUsername())
                .build();
    }

    public List<TransferResponse> toResponseList(List<StockTransfer> transfers) {
        if (transfers == null) return null;
        return transfers.stream()
                .map(this::toResponse)
                .toList();
    }
}