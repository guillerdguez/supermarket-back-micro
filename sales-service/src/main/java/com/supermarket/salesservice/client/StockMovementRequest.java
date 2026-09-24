package com.supermarket.salesservice.client;

import java.util.List;

public record StockMovementRequest(List<StockItem> items) {
}
