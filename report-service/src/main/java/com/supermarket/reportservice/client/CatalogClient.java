package com.supermarket.reportservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "catalog-service", contextId = "catalogClient", path = "/internal/products")
public interface CatalogClient {

    @GetMapping
    List<ProductSummary> getAll();

    @GetMapping
    List<ProductSummary> getByIds(@RequestParam("ids") Collection<Long> ids);
}
