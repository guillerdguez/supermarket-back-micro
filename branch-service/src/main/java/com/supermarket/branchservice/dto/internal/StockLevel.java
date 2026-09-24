package com.supermarket.branchservice.dto.internal;

public record StockLevel(Long branchId, Long productId, Integer stock, Integer minStock) {
}
