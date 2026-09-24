package com.supermarket.branchservice.dto.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockItem(
        @NotNull(message = "Product ID is required") Long productId,
        @NotNull(message = "Quantity is required") @Min(value = 1, message = "Minimum quantity allowed is 1") Integer quantity) {
}
