package com.supermarket.branchservice.mapper;

import com.supermarket.branchservice.client.ProductSummary;
import com.supermarket.branchservice.dto.inventory.BranchInventoryResponse;
import com.supermarket.branchservice.model.branch.BranchInventory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BranchInventoryMapper {

    public BranchInventoryResponse toResponse(BranchInventory inventory, ProductSummary product) {
        if (inventory == null) {
            return null;
        }

        return BranchInventoryResponse.builder()
                .id(inventory.getId())
                .branchId(inventory.getBranch() != null ? inventory.getBranch().getId() : null)
                .branchName(inventory.getBranch() != null ? inventory.getBranch().getName() : null)
                .productId(inventory.getProductId())
                .productName(product != null ? product.name() : null)
                .productCategory(product != null ? product.category() : null)
                .stock(inventory.getStock())
                .minStock(inventory.getMinStock())
                .lastRestockDate(inventory.getLastRestockDate())
                .build();
    }

    public List<BranchInventoryResponse> toResponseList(List<BranchInventory> inventories,
                                                        Map<Long, ProductSummary> products) {
        if (inventories == null) {
            return null;
        }
        return inventories.stream()
                .map(inventory -> toResponse(inventory, products.get(inventory.getProductId())))
                .toList();
    }
}
