package com.supermarket.branchservice.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StockMovementRequest(
        @NotEmpty(message = "At least one item is required") List<@Valid StockItem> items) {
}
