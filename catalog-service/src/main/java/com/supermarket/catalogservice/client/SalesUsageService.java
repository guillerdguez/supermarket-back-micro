package com.supermarket.catalogservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesUsageService {

    private static final String SALES_SERVICE = "sales-service";

    private final SalesUsageClient salesUsageClient;

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "unavailable")
    @Retry(name = SALES_SERVICE)
    public boolean isProductInUse(Long productId) {
        return salesUsageClient.isProductInUse(productId);
    }

    private boolean unavailable(Long productId, Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }
}
