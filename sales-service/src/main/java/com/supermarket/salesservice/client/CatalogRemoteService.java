package com.supermarket.salesservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import com.supermarket.commons.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogRemoteService {

    private static final String CATALOG_SERVICE = "catalog-service";

    private final CatalogClient catalogClient;

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "requireProductsFallback")
    @Retry(name = CATALOG_SERVICE)
    public Map<Long, ProductSummary> requireProducts(Set<Long> productIds) {
        Map<Long, ProductSummary> products = catalogClient.getByIds(productIds).stream()
                .collect(Collectors.toMap(ProductSummary::id, Function.identity(), (a, b) -> a));
        if (products.size() != productIds.size()) {
            List<Long> missingIds = productIds.stream()
                    .filter(id -> !products.containsKey(id))
                    .sorted()
                    .toList();
            throw new ResourceNotFoundException("Products not found with IDs: " + missingIds);
        }
        return products;
    }

    private Map<Long, ProductSummary> requireProductsFallback(Set<Long> productIds, Throwable throwable) {
        throw RemoteFailures.propagate(CATALOG_SERVICE, throwable);
    }
}
