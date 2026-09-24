package com.supermarket.branchservice.controller.internal;

import com.supermarket.branchservice.dto.branch.BranchResponse;
import com.supermarket.branchservice.service.business.BranchService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/branches")
@RequiredArgsConstructor
public class InternalBranchController {

    private final BranchService branchService;

    @GetMapping
    public List<BranchResponse> getBranches(@RequestParam(value = "ids", required = false) List<Long> ids) {
        return ids == null ? branchService.getAll(true) : branchService.getByIds(ids);
    }

    @GetMapping("/{id}")
    public BranchResponse getById(@PathVariable Long id) {
        return branchService.getById(id);
    }

    @GetMapping("/warehouse")
    public BranchResponse getWarehouse() {
        return branchService.getWarehouse();
    }
}
