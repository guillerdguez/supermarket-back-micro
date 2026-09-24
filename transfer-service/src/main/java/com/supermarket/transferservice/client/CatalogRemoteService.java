package com.supermarket.transferservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import com.supermarket.commons.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogRemoteService {

    private static final String CATALOG_SERVICE = "catalog-service";

    private final CatalogClient catalogClient;

    @CircuitBreaker(name = CATALOG_SERVICE, fallbackMethod = "requireProductFallback")
    @Retry(name = CATALOG_SERVICE)
    public ProductSummary requireProduct(Long productId) {
        try {
            return catalogClient.getById(productId);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Product not found");
        }
    }

    private ProductSummary requireProductFallback(Long productId, Throwable throwable) {
        throw RemoteFailures.propagate(CATALOG_SERVICE, throwable);
    }
}
