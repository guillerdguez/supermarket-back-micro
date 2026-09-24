package com.supermarket.reportservice.client;

import java.math.BigDecimal;

public record PeriodSummaryRow(BigDecimal totalRevenue, Long transactionCount) {
}
