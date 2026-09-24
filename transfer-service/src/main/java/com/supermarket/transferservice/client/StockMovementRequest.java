package com.supermarket.transferservice.client;

import java.util.List;

public record StockMovementRequest(List<StockItem> items) {
}
