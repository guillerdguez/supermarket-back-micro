package com.supermarket.branchservice.dto.internal;

import java.util.List;

public record StockMovementResponse(Long branchId, String idempotencyKey, String operation,
                                    boolean applied, List<StockItem> items) {
}
