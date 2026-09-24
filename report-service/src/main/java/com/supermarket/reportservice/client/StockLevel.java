package com.supermarket.reportservice.client;

public record StockLevel(Long branchId, Long productId, Integer stock, Integer minStock) {
}
