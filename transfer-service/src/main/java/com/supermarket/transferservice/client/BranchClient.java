package com.supermarket.transferservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "branch-service", contextId = "branchClient", path = "/internal")
public interface BranchClient {

    String IDEMPOTENCY_KEY = "Idempotency-Key";

    @GetMapping("/branches/{id}")
    BranchSummary getBranch(@PathVariable("id") Long id);

    @GetMapping("/branches/warehouse")
    BranchSummary getWarehouse();

    @GetMapping("/inventory/branches/{branchId}/products/{productId}/stock")
    Integer getStock(@PathVariable("branchId") Long branchId, @PathVariable("productId") Long productId);

    @PostMapping("/inventory/branches/{branchId}/decrease")
    StockMovementResponse decreaseStock(@PathVariable("branchId") Long branchId,
                                        @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                        @RequestBody StockMovementRequest request);

    @PostMapping("/inventory/branches/{branchId}/increase")
    StockMovementResponse increaseStock(@PathVariable("branchId") Long branchId,
                                        @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                        @RequestBody StockMovementRequest request);
}
