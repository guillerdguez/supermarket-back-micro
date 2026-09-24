package com.supermarket.transferservice.service.business.impl;

import com.supermarket.commons.exception.InsufficientPermissionsException;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.transferservice.client.BranchRemoteService;
import com.supermarket.transferservice.client.BranchSummary;
import com.supermarket.transferservice.client.CatalogRemoteService;
import com.supermarket.transferservice.client.ProductSummary;
import com.supermarket.transferservice.dto.transfer.RejectTransferRequest;
import com.supermarket.transferservice.dto.transfer.TransferRequest;
import com.supermarket.transferservice.dto.transfer.TransferResponse;
import com.supermarket.transferservice.event.TransferEventPublisher;
import com.supermarket.transferservice.mapper.TransferMapper;
import com.supermarket.transferservice.model.transfer.CompletionState;
import com.supermarket.transferservice.model.transfer.StockTransfer;
import com.supermarket.transferservice.model.transfer.TransferStatus;
import com.supermarket.transferservice.repository.StockTransferRepository;
import com.supermarket.transferservice.service.business.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {
    private final StockTransferRepository transferRepository;
    private final BranchRemoteService branchRemoteService;
    private final CatalogRemoteService catalogRemoteService;
    private final TransferMapper transferMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TransferEventPublisher transferEventPublisher;

    @Override
    public TransferResponse requestTransfer(TransferRequest request) {
        log.info("Requesting transfer: source={}, target={}, product={}, quantity={}",
                request.getSourceBranchId(), request.getTargetBranchId(),
                request.getProductId(), request.getQuantity());
        if (request.getSourceBranchId() != null
                && request.getSourceBranchId().equals(request.getTargetBranchId())) {
            throw new InvalidOperationException("Source and target branches must be different");
        }
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        BranchSummary source = resolveSourceBranch(request.getSourceBranchId());
        BranchSummary target = resolveTargetBranch(request.getTargetBranchId(), currentUser);
        if (source.id().equals(target.id())) {
            throw new InvalidOperationException("Source and target branches must be different");
        }
        ProductSummary product = catalogRemoteService.requireProduct(request.getProductId());
        Integer availableStock = branchRemoteService.getStock(source.id(), product.id());
        if (availableStock < request.getQuantity()) {
            throw new InsufficientStockException(
                    String.format("Insufficient stock in source branch. Available: %d, requested: %d",
                            availableStock, request.getQuantity()));
        }
        StockTransfer transfer = StockTransfer.builder()
                .sourceBranchId(source.id())
                .sourceBranchName(source.name())
                .targetBranchId(target.id())
                .targetBranchName(target.name())
                .productId(product.id())
                .productName(product.name())
                .quantity(request.getQuantity())
                .status(TransferStatus.PENDING)
                .requestedById(currentUser.id())
                .requestedByUsername(currentUser.username())
                .requestedAt(LocalDateTime.now())
                .build();
        StockTransfer saved = transferRepository.save(transfer);
        transferEventPublisher.transferRequested(saved);
        log.info("Transfer requested with id: {}", saved.getId());
        return transferMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TransferResponse approveTransfer(Long transferId) {
        log.info("Approving transfer id: {}", transferId);
        StockTransfer transfer = findTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new InvalidOperationException("Only PENDING transfers can be approved");
        }
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        transfer.setStatus(TransferStatus.APPROVED);
        transfer.setApprovedById(currentUser.id());
        transfer.setApprovedByUsername(currentUser.username());
        transfer.setApprovedAt(LocalDateTime.now());
        StockTransfer saved = transferRepository.save(transfer);
        transferEventPublisher.transferApproved(saved);
        log.info("Transfer approved with id: {}", transferId);
        return transferMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TransferResponse rejectTransfer(Long transferId, RejectTransferRequest request) {
        log.info("Rejecting transfer id: {}", transferId);
        StockTransfer transfer = findTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new InvalidOperationException("Only PENDING transfers can be rejected");
        }
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        transfer.setStatus(TransferStatus.REJECTED);
        transfer.setApprovedById(currentUser.id());
        transfer.setApprovedByUsername(currentUser.username());
        transfer.setApprovedAt(LocalDateTime.now());
        transfer.setRejectionReason(request.getReason());
        StockTransfer saved = transferRepository.save(transfer);
        transferEventPublisher.transferRejected(saved);
        log.info("Transfer rejected with id: {}", transferId);
        return transferMapper.toResponse(saved);
    }

    @Override
    public TransferResponse completeTransfer(Long transferId) {
        log.info("Completing transfer id: {}", transferId);
        StockTransfer transfer = findTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new InvalidOperationException("Only APPROVED transfers can be completed");
        }
        branchRemoteService.requireBranch(transfer.getTargetBranchId(), "Target branch no longer exists");

        if (transfer.getCompletionState() == CompletionState.COMPENSATING) {
            transfer = compensateSource(transfer);
        }
        if (transfer.getCompletionAttempt() == 0) {
            transfer.setCompletionAttempt(1);
            transfer = transferRepository.save(transfer);
        }
        if (transfer.getCompletionState() == CompletionState.NONE) {
            transfer = debitSource(transfer);
        }
        return creditTarget(transfer);
    }

    private StockTransfer debitSource(StockTransfer transfer) {
        branchRemoteService.decreaseStock(transfer.getSourceBranchId(), sourceKey(transfer),
                transfer.getProductId(), transfer.getQuantity());
        transfer.setCompletionState(CompletionState.SOURCE_DEBITED);
        return transferRepository.save(transfer);
    }

    private TransferResponse creditTarget(StockTransfer transfer) {
        try {
            branchRemoteService.increaseStock(transfer.getTargetBranchId(), targetKey(transfer),
                    transfer.getProductId(), transfer.getQuantity());
        } catch (RuntimeException targetFailure) {
            log.warn("Crediting target branch {} failed for transfer {}. Compensating source branch {}",
                    transfer.getTargetBranchId(), transfer.getId(), transfer.getSourceBranchId());
            transfer.setCompletionState(CompletionState.COMPENSATING);
            transfer = transferRepository.save(transfer);
            compensateSource(transfer);
            throw targetFailure;
        }
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(LocalDateTime.now());
        transfer.setCompletionState(CompletionState.NONE);
        StockTransfer saved = transferRepository.save(transfer);
        transferEventPublisher.transferCompleted(saved);
        log.info("Transfer completed with id: {}", transfer.getId());
        return transferMapper.toResponse(saved);
    }

    private StockTransfer compensateSource(StockTransfer transfer) {
        branchRemoteService.increaseStock(transfer.getSourceBranchId(), compensationKey(transfer),
                transfer.getProductId(), transfer.getQuantity());
        transfer.setCompletionState(CompletionState.NONE);
        transfer.setCompletionAttempt(transfer.getCompletionAttempt() + 1);
        StockTransfer saved = transferRepository.save(transfer);
        log.info("Source branch {} compensated for transfer {}", transfer.getSourceBranchId(), transfer.getId());
        return saved;
    }

    public static String sourceKey(StockTransfer transfer) {
        return stepKey(transfer, "source");
    }

    public static String targetKey(StockTransfer transfer) {
        return stepKey(transfer, "target");
    }

    public static String compensationKey(StockTransfer transfer) {
        return stepKey(transfer, "compensation");
    }

    private static String stepKey(StockTransfer transfer, String step) {
        return "transfer-" + transfer.getId() + "-attempt-" + transfer.getCompletionAttempt() + "-" + step;
    }

    @Override
    @Transactional
    public TransferResponse cancelTransfer(Long transferId) {
        log.info("Cancelling transfer id: {}", transferId);
        StockTransfer transfer = findTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new InvalidOperationException("Only PENDING transfers can be cancelled");
        }
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        boolean isRequester = currentUser.id().equals(transfer.getRequestedById());
        if (!isRequester && !currentUser.isAdmin()) {
            throw new InsufficientPermissionsException("You are not allowed to cancel this transfer");
        }
        transfer.setStatus(TransferStatus.CANCELLED);
        StockTransfer saved = transferRepository.save(transfer);
        log.info("Transfer cancelled with id: {}", transferId);
        return transferMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferResponse> getAllTransfers() {
        return transferMapper.toResponseList(
                transferRepository.findAll(Sort.by(Sort.Direction.DESC, "requestedAt")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferResponse> getMyTransfers() {
        return transferMapper.toResponseList(
                transferRepository.findByRequestedById(currentUserProvider.getCurrentUser().id()));
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long id) {
        return transferMapper.toResponse(findTransfer(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferResponse> getTransfersByStatus(String status) {
        try {
            TransferStatus transferStatus = TransferStatus.valueOf(status.toUpperCase());
            return transferMapper.toResponseList(transferRepository.findByStatus(transferStatus));
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Invalid transfer status: " + status);
        }
    }

    @Override
    public List<TransferResponse> getTransfersBySourceBranch(Long branchId) {
        branchRemoteService.requireBranch(branchId, "Branch not found with id: " + branchId);
        return transferMapper.toResponseList(transferRepository.findBySourceBranchId(branchId));
    }

    @Override
    public List<TransferResponse> getTransfersByTargetBranch(Long branchId) {
        branchRemoteService.requireBranch(branchId, "Branch not found with id: " + branchId);
        return transferMapper.toResponseList(transferRepository.findByTargetBranchId(branchId));
    }

    private BranchSummary resolveSourceBranch(Long sourceBranchId) {
        if (sourceBranchId != null) {
            BranchSummary source = branchRemoteService.requireBranch(sourceBranchId, "Source branch not found");
            if (!Boolean.TRUE.equals(source.isWarehouse())) {
                throw new InvalidOperationException(
                        "Stock requests must originate from the central warehouse");
            }
            return source;
        }
        try {
            return branchRemoteService.requireWarehouse();
        } catch (ResourceNotFoundException e) {
            throw new InvalidOperationException("No central warehouse branch is configured");
        }
    }

    private BranchSummary resolveTargetBranch(Long targetBranchId, AuthenticatedUser currentUser) {
        if (targetBranchId != null) {
            return branchRemoteService.requireBranch(targetBranchId, "Target branch not found");
        }
        if (currentUser.branchId() == null) {
            throw new InvalidOperationException("This user has no branch assigned");
        }
        return branchRemoteService.requireBranch(currentUser.branchId(), "Target branch not found");
    }

    private StockTransfer findTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with id: " + id));
    }
}
