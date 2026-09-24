package com.supermarket.salesservice.dto.report;

import java.math.BigDecimal;

public record PeriodSummaryRow(BigDecimal totalRevenue, Long transactionCount) {
}
