package com.supermarket.branchservice.service.business;

import com.supermarket.branchservice.dto.branch.BranchRequest;
import com.supermarket.branchservice.dto.branch.BranchResponse;

import java.util.List;

public interface BranchService {
    List<BranchResponse> getAll(boolean includeInactive);

    BranchResponse getById(Long id);

    List<BranchResponse> getByIds(List<Long> ids);

    BranchResponse getWarehouse();

    BranchResponse create(BranchRequest branch);

    BranchResponse update(Long id, BranchRequest branch);

    void deactivate(Long id);

    void reactivate(Long id);

    void delete(Long id);

}