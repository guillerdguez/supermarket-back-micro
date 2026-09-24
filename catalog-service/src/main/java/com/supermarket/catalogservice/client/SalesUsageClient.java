package com.supermarket.catalogservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "sales-service", contextId = "salesUsageClient", path = "/internal/sales")
public interface SalesUsageClient {

    @GetMapping("/products/{productId}/in-use")
    boolean isProductInUse(@PathVariable("productId") Long productId);
}
