package com.supermarket.branchservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchUsageService {

    private final SalesUsageClient salesUsageClient;
    private final TransferUsageClient transferUsageClient;
    private final UserUsageClient userUsageClient;

    @CircuitBreaker(name = "sales-service", fallbackMethod = "salesUnavailable")
    @Retry(name = "sales-service")
    public SalesBranchUsage salesUsage(Long branchId) {
        return salesUsageClient.getBranchUsage(branchId);
    }

    @CircuitBreaker(name = "transfer-service", fallbackMethod = "transfersUnavailable")
    @Retry(name = "transfer-service")
    public boolean hasTransfers(Long branchId) {
        return transferUsageClient.existsForBranch(branchId);
    }

    @CircuitBreaker(name = "auth-service", fallbackMethod = "usersUnavailable")
    @Retry(name = "auth-service")
    public boolean hasUsers(Long branchId) {
        return userUsageClient.existsForBranch(branchId);
    }

    private SalesBranchUsage salesUnavailable(Long branchId, Throwable throwable) {
        throw RemoteFailures.propagate("sales-service", throwable);
    }

    private boolean transfersUnavailable(Long branchId, Throwable throwable) {
        throw RemoteFailures.propagate("transfer-service", throwable);
    }

    private boolean usersUnavailable(Long branchId, Throwable throwable) {
        throw RemoteFailures.propagate("auth-service", throwable);
    }
}
