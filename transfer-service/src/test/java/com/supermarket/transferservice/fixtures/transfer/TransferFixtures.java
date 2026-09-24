package com.supermarket.transferservice.fixtures.transfer;

import com.supermarket.transferservice.dto.transfer.RejectTransferRequest;
import com.supermarket.transferservice.dto.transfer.TransferRequest;
import com.supermarket.transferservice.dto.transfer.TransferResponse;
import com.supermarket.transferservice.model.transfer.StockTransfer;
import com.supermarket.transferservice.model.transfer.TransferStatus;
import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.transferservice.client.BranchSummary;
import com.supermarket.transferservice.client.ProductSummary;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

import java.time.LocalDateTime;

@UtilityClass
public class TransferFixtures {

    public static final Long WAREHOUSE_ID = 6L;

    public static StockTransfer pendingTransfer() {
        return StockTransfer.builder()
                .id(1L)
                .sourceBranchId(WAREHOUSE_ID)
                .sourceBranchName("Central Warehouse")
                .targetBranchId(2L)
                .targetBranchName("North Branch")
                .productId(1L)
                .productName("Premium Rice")
                .quantity(10)
                .status(TransferStatus.PENDING)
                .requestedById(1L)
                .requestedByUsername("cashier-test")
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public static StockTransfer approvedTransfer() {
        StockTransfer t = pendingTransfer();
        t.setStatus(TransferStatus.APPROVED);
        t.setApprovedById(2L);
        t.setApprovedByUsername("manager-test");
        t.setApprovedAt(LocalDateTime.now());
        return t;
    }

    public static BranchSummary warehouse() {
        return new BranchSummary(WAREHOUSE_ID, "Central Warehouse", "1 Industrial Park", true, true);
    }

    public static BranchSummary northBranch() {
        return new BranchSummary(2L, "North Branch", "456 North Ave", false, true);
    }

    public static ProductSummary product() {
        return new ProductSummary(1L, "Premium Rice", "8410000000001", "Food", new BigDecimal("2.50"));
    }

    public static AuthenticatedUser cashier() {
        return new AuthenticatedUser(1L, "cashier@test.com", "cashier-test", "CASHIER", 2L);
    }

    public static AuthenticatedUser manager() {
        return new AuthenticatedUser(2L, "manager@test.com", "manager-test", "MANAGER", null);
    }

    public static AuthenticatedUser admin() {
        return new AuthenticatedUser(3L, "admin@test.com", "admin-test", "ADMIN", null);
    }

    public static TransferRequest validTransferRequest() {
        return TransferRequest.builder()
                .sourceBranchId(WAREHOUSE_ID)
                .targetBranchId(2L)
                .productId(1L)
                .quantity(10)
                .build();
    }

    public static TransferRequest sameBranchRequest() {
        return TransferRequest.builder()
                .sourceBranchId(1L)
                .targetBranchId(1L)
                .productId(1L)
                .quantity(10)
                .build();
    }

    public static RejectTransferRequest validRejectRequest() {
        return RejectTransferRequest.builder()
                .reason("Stock needed locally for upcoming promotion")
                .build();
    }

    public static TransferResponse transferResponse(TransferStatus status) {
        return TransferResponse.builder()
                .id(1L)
                .sourceBranchId(WAREHOUSE_ID)
                .sourceBranchName("Central Warehouse")
                .targetBranchId(2L)
                .targetBranchName("North Branch")
                .productId(1L)
                .productName("Premium Rice")
                .quantity(10)
                .status(status)
                .requestedById(1L)
                .requestedByUsername("cashier-test")
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public static TransferResponse transferResponse() {
        return transferResponse(TransferStatus.PENDING);
    }
}