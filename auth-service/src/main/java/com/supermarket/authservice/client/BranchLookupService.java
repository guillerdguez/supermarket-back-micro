package com.supermarket.authservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BranchLookupService {

    private static final String BRANCH_SERVICE = "branch-service";

    private final BranchClient branchClient;

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "requireBranchFallback")
    @Retry(name = BRANCH_SERVICE)
    public BranchSummary requireBranch(Long branchId) {
        return branchClient.getById(branchId);
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "branchNamesFallback")
    public Map<Long, String> branchNames(Collection<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return Map.of();
        }
        return branchClient.getByIds(branchIds).stream()
                .collect(Collectors.toMap(BranchSummary::id, BranchSummary::name, (a, b) -> a));
    }

    private BranchSummary requireBranchFallback(Long branchId, Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }

    private Map<Long, String> branchNamesFallback(Collection<Long> branchIds, Throwable throwable) {
        log.warn("Branch names unavailable for {}: {}", branchIds, throwable.getMessage());
        return Map.of();
    }
}
