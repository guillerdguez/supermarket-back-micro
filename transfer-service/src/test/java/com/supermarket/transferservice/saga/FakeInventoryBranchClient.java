package com.supermarket.transferservice.saga;

import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.transferservice.client.BranchClient;
import com.supermarket.transferservice.client.BranchSummary;
import com.supermarket.transferservice.client.StockItem;
import com.supermarket.transferservice.client.StockMovementRequest;
import com.supermarket.transferservice.client.StockMovementResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class FakeInventoryBranchClient implements BranchClient {

    private final Map<Long, Integer> stockByBranch = new HashMap<>();
    private final Set<String> processedKeys = new HashSet<>();
    private int targetFailuresLeft;
    private int compensationFailuresLeft;
    private final Long targetBranchId;
    private final Long sourceBranchId;

    FakeInventoryBranchClient(Long sourceBranchId, int sourceStock, Long targetBranchId, int targetStock) {
        this.sourceBranchId = sourceBranchId;
        this.targetBranchId = targetBranchId;
        stockByBranch.put(sourceBranchId, sourceStock);
        stockByBranch.put(targetBranchId, targetStock);
    }

    void failNextTargetCredits(int times) {
        targetFailuresLeft = times;
    }

    void failNextCompensations(int times) {
        compensationFailuresLeft = times;
    }

    int stockOf(Long branchId) {
        return stockByBranch.get(branchId);
    }

    @Override
    public BranchSummary getBranch(Long id) {
        if (!stockByBranch.containsKey(id)) {
            throw new ResourceNotFoundException("Branch not found with ID: " + id);
        }
        return new BranchSummary(id, "Branch " + id, "Street " + id, id.equals(sourceBranchId), true);
    }

    @Override
    public BranchSummary getWarehouse() {
        return getBranch(sourceBranchId);
    }

    @Override
    public Integer getStock(Long branchId, Long productId) {
        return stockByBranch.get(branchId);
    }

    @Override
    public StockMovementResponse decreaseStock(Long branchId, String idempotencyKey, StockMovementRequest request) {
        return apply(branchId, idempotencyKey, request, -1);
    }

    @Override
    public StockMovementResponse increaseStock(Long branchId, String idempotencyKey, StockMovementRequest request) {
        if (branchId.equals(targetBranchId) && targetFailuresLeft > 0) {
            targetFailuresLeft--;
            throw new RemoteServiceException("branch-service", "branch-service is temporarily unavailable");
        }
        if (idempotencyKey.endsWith("-compensation") && compensationFailuresLeft > 0) {
            compensationFailuresLeft--;
            throw new RemoteServiceException("branch-service", "branch-service is temporarily unavailable");
        }
        return apply(branchId, idempotencyKey, request, 1);
    }

    private StockMovementResponse apply(Long branchId, String key, StockMovementRequest request, int sign) {
        if (!processedKeys.add(key)) {
            return new StockMovementResponse(branchId, key, sign < 0 ? "DECREASE" : "INCREASE", false, request.items());
        }
        int delta = request.items().stream().mapToInt(StockItem::quantity).sum() * sign;
        stockByBranch.merge(branchId, delta, Integer::sum);
        return new StockMovementResponse(branchId, key, sign < 0 ? "DECREASE" : "INCREASE", true, request.items());
    }
}
