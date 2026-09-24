package com.supermarket.transferservice.client;

import java.math.BigDecimal;

public record ProductSummary(Long id, String name, String barcode, String category, BigDecimal price) {
}
