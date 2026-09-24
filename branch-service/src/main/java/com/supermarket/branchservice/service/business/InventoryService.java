package com.supermarket.branchservice.service.business;

import com.supermarket.branchservice.dto.internal.StockItem;
import com.supermarket.branchservice.dto.internal.StockLevel;
import com.supermarket.branchservice.dto.inventory.BranchInventoryResponse;
import com.supermarket.branchservice.dto.inventory.LowStockAlertResponse;
import com.supermarket.branchservice.dto.inventory.StockAdjustmentRequest;
import com.supermarket.branchservice.dto.inventory.StockUpdateRequest;
import com.supermarket.branchservice.dto.inventory.TotalStockResponse;
import com.supermarket.branchservice.model.branch.Branch;

import java.util.List;

public interface InventoryService {

    Integer getStockInBranch(Long branchId, Long productId);

    Integer getMinStockInBranch(Long branchId, Long productId);

    List<LowStockAlertResponse> getLowStockInBranch(Long branchId);

    List<LowStockAlertResponse> getLowStockGlobal();

    List<BranchInventoryResponse> getBranchInventory(Long branchId);

    TotalStockResponse getTotalStockByProduct(Long productId);

    BranchInventoryResponse updateStock(Long branchId, Long productId, StockUpdateRequest request);

    BranchInventoryResponse adjustStock(Long branchId, Long productId, StockAdjustmentRequest request);

    void decreaseStockBatch(Long branchId, List<StockItem> items);

    void increaseStockBatch(Long branchId, List<StockItem> items);

    void initializeInventoryForNewProduct(Long productId);

    void initializeInventoryForNewBranch(Branch branch);

    void removeProduct(Long productId);

    List<StockLevel> getStockLevels(Long branchId);
}
