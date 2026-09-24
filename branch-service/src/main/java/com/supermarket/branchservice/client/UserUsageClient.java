package com.supermarket.branchservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", contextId = "userUsageClient", path = "/internal/users")
public interface UserUsageClient {

    @GetMapping("/branches/{branchId}/exists")
    boolean existsForBranch(@PathVariable("branchId") Long branchId);
}
