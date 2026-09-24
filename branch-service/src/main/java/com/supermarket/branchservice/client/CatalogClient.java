package com.supermarket.branchservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "catalog-service", contextId = "catalogClient", path = "/internal/products")
public interface CatalogClient {

    @GetMapping("/{id}")
    ProductSummary getById(@PathVariable("id") Long id);

    @GetMapping
    List<ProductSummary> getByIds(@RequestParam("ids") Collection<Long> ids);

    @GetMapping
    List<ProductSummary> getAll();
}
