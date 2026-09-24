package com.supermarket.transferservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import com.supermarket.commons.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchRemoteService {

    private static final String BRANCH_SERVICE = "branch-service";

    private final BranchClient branchClient;

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "branchFallback")
    @Retry(name = BRANCH_SERVICE)
    public BranchSummary requireBranch(Long branchId, String notFoundMessage) {
        try {
            return branchClient.getBranch(branchId);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "warehouseFallback")
    @Retry(name = BRANCH_SERVICE)
    public BranchSummary requireWarehouse() {
        return branchClient.getWarehouse();
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "stockFallback")
    @Retry(name = BRANCH_SERVICE)
    public Integer getStock(Long branchId, Long productId) {
        return branchClient.getStock(branchId, productId);
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "movementFallback")
    @Retry(name = BRANCH_SERVICE)
    public StockMovementResponse decreaseStock(Long branchId, String idempotencyKey, Long productId, Integer quantity) {
        return branchClient.decreaseStock(branchId, idempotencyKey,
                new StockMovementRequest(List.of(new StockItem(productId, quantity))));
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "movementFallback")
    @Retry(name = BRANCH_SERVICE)
    public StockMovementResponse increaseStock(Long branchId, String idempotencyKey, Long productId, Integer quantity) {
        return branchClient.increaseStock(branchId, idempotencyKey,
                new StockMovementRequest(List.of(new StockItem(productId, quantity))));
    }

    private BranchSummary branchFallback(Long branchId, String notFoundMessage, Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }

    private BranchSummary warehouseFallback(Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }

    private Integer stockFallback(Long branchId, Long productId, Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }

    private StockMovementResponse movementFallback(Long branchId, String idempotencyKey, Long productId,
                                                   Integer quantity, Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }
}
