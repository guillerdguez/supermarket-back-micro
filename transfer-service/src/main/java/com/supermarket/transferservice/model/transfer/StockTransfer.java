package com.supermarket.transferservice.model.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_transfers")
public class StockTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_branch_id", nullable = false)
    private Long sourceBranchId;

    @Column(name = "source_branch_name")
    private String sourceBranchName;

    @Column(name = "target_branch_id", nullable = false)
    private Long targetBranchId;

    @Column(name = "target_branch_name")
    private String targetBranchName;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    @Column(name = "requested_by_username")
    private String requestedByUsername;

    @Column(name = "approved_by_id")
    private Long approvedById;

    @Column(name = "approved_by_username")
    private String approvedByUsername;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "completion_attempt", nullable = false)
    @Builder.Default
    private Integer completionAttempt = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_state", nullable = false, length = 20)
    @Builder.Default
    private CompletionState completionState = CompletionState.NONE;

    @Version
    private Long version;
}