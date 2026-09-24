package com.supermarket.reportservice.client;

import java.math.BigDecimal;

public record SalesByProductRow(Long productId, String productName, String productCategory, Long totalQuantitySold, BigDecimal totalRevenue) {
}
