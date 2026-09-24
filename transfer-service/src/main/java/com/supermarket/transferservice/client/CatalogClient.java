package com.supermarket.transferservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service", contextId = "catalogClient", path = "/internal/products")
public interface CatalogClient {

    @GetMapping("/{id}")
    ProductSummary getById(@PathVariable("id") Long id);
}
