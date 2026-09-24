package com.supermarket.salesservice.dto.report;

import java.math.BigDecimal;

public record SalesByBranchRow(Long branchId, String branchName, BigDecimal totalRevenue, Long transactionCount) {
}
