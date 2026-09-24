package com.supermarket.reportservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "branch-service", contextId = "branchClient", path = "/internal")
public interface BranchClient {

    @GetMapping("/branches")
    List<BranchSummary> getBranches(@RequestParam("ids") Collection<Long> ids);

    @GetMapping("/inventory/stock-levels")
    List<StockLevel> getStockLevels(@RequestParam(value = "branchId", required = false) Long branchId);
}
