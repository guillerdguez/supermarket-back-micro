package com.supermarket.reportservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryRemote {

    private static final String BRANCH_SERVICE = "branch-service";
    private static final String CATALOG_SERVICE = "catalog-service";
    private static final String AUTH_SERVICE = "auth-service";

    private final BranchClient branchClient;
    private final CatalogClient catalogClient;
    private final UserClient userClient;

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "stockLevelsFallback")
    @Retry(name = BRANCH_SERVICE)
    public List<StockLevel> stockLevels(Long branchId) {
        return branchClient.getStockLevels(branchId);
    }

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "allProductsFallback")
    @Retry(name = CATALOG_SERVICE)
    public List<ProductSummary> allProducts() {
        return catalogClient.getAll();
    }

    @CircuitBreaker(name = BRANCH_SERVICE, fallbackMethod = "namesFallback")
    public Map<Long, String> branchNames(Collection<Long> branchIds) {
        if (branchIds.isEmpty()) {
            return Map.of();
        }
        return branchClient.getBranches(branchIds).stream()
                .collect(Collectors.toMap(BranchSummary::id, BranchSummary::name, (a, b) -> a));
    }

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "productsFallback")
    public Map<Long, ProductSummary> productsById(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return catalogClient.getByIds(productIds).stream()
                .collect(Collectors.toMap(ProductSummary::id, Function.identity(), (a, b) -> a));
    }

    @CircuitBreaker(name = AUTH_SERVICE, fallbackMethod = "namesFallback")
    public Map<Long, String> usernames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userClient.getByIds(userIds).stream()
                .collect(Collectors.toMap(UserSummary::id, UserSummary::username, (a, b) -> a));
    }

    private List<StockLevel> stockLevelsFallback(Long branchId, Throwable throwable) {
        throw RemoteFailures.propagate(BRANCH_SERVICE, throwable);
    }

    private List<ProductSummary> allProductsFallback(Throwable throwable) {
        throw RemoteFailures.propagate(CATALOG_SERVICE, throwable);
    }

    private Map<Long, String> namesFallback(Collection<Long> ids, Throwable throwable) {
        log.warn("Names unavailable for {}: {}. Falling back to names frozen in sales", ids, throwable.getMessage());
        return Map.of();
    }

    private Map<Long, ProductSummary> productsFallback(Collection<Long> ids, Throwable throwable) {
        log.warn("Product details unavailable for {}: {}. Falling back to names frozen in sales", ids,
                throwable.getMessage());
        return Map.of();
    }
}
