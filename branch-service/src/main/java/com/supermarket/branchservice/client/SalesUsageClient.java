package com.supermarket.branchservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "sales-service", contextId = "salesUsageClient", path = "/internal/sales")
public interface SalesUsageClient {

    @GetMapping("/branches/{branchId}/usage")
    SalesBranchUsage getBranchUsage(@PathVariable("branchId") Long branchId);
}
