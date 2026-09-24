package com.supermarket.salesservice.client;

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

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "requireBranchFallback")
    @Retry(name = BRANCH_SERVICE)
    public BranchSummary requireBranch(Long branchId) {
        try {
            return branchClient.getBranch(branchId);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Branch not found");
        }
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "stockMovementFallback")
    @Retry(name = BRANCH_SERVICE)
    public StockMovementResponse decreaseStock(Long branchId, String idempotencyKey, List<StockItem> items) {
        return branchClient.decreaseStock(branchId, idempotencyKey, new StockMovementRequest(items));
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "stockMovementFallback")
    @Retry(name = BRANCH_SERVICE)
    public StockMovementResponse increaseStock(Long branchId, String idempotencyKey, List<StockItem> items) {
        return branchClient.increaseStock(branchId, idempotencyKey, new StockMovementRequest(items));
    }

    private BranchSummary requireBranchFallback(Long branchId, Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }

    private StockMovementResponse stockMovementFallback(Long branchId, String idempotencyKey, List<StockItem> items,
                                                        Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }
}
