package com.supermarket.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "branch-service", contextId = "branchClient", path = "/internal/branches")
public interface BranchClient {

    @GetMapping("/{id}")
    BranchSummary getById(@PathVariable("id") Long id);

    @GetMapping
    List<BranchSummary> getByIds(@RequestParam("ids") Collection<Long> ids);
}
