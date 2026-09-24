package com.supermarket.branchservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import com.supermarket.commons.exception.ResourceNotFoundException;
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
public class CatalogLookupService {

    private static final String CATALOG_SERVICE = "catalog-service";

    private final CatalogClient catalogClient;

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "requireProductFallback")
    @Retry(name = CATALOG_SERVICE)
    public ProductSummary requireProduct(Long productId) {
        try {
            return catalogClient.getById(productId);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }
    }

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "productsByIdFallback")
    public Map<Long, ProductSummary> productsById(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return catalogClient.getByIds(productIds).stream()
                .collect(Collectors.toMap(ProductSummary::id, Function.identity(), (a, b) -> a));
    }

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "allProductIdsFallback")
    @Retry(name = CATALOG_SERVICE)
    public List<Long> allProductIds() {
        return catalogClient.getAll().stream().map(ProductSummary::id).toList();
    }

    private ProductSummary requireProductFallback(Long productId, Throwable throwable) {
        throw RemoteFailures.propagate(CATALOG_SERVICE, throwable);
    }

    private Map<Long, ProductSummary> productsByIdFallback(Collection<Long> productIds, Throwable throwable) {
        log.warn("Product details unavailable for {}: {}", productIds, throwable.getMessage());
        return Map.of();
    }

    private List<Long> allProductIdsFallback(Throwable throwable) {
        throw RemoteFailures.propagate(CATALOG_SERVICE, throwable);
    }
}
