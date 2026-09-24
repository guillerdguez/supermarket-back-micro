package com.supermarket.branchservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "transfer-service", contextId = "transferUsageClient", path = "/internal/transfers")
public interface TransferUsageClient {

    @GetMapping("/branches/{branchId}/exists")
    boolean existsForBranch(@PathVariable("branchId") Long branchId);
}
