package com.supermarket.salesservice.dto.report;

import java.math.BigDecimal;

public record SalesByCashierRow(Long cashierId, String cashierUsername, BigDecimal totalRevenue,
                                Long transactionCount) {
}
