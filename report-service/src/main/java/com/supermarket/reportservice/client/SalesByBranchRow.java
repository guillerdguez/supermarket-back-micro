package com.supermarket.reportservice.client;

import java.math.BigDecimal;

public record SalesByBranchRow(Long branchId, String branchName, BigDecimal totalRevenue, Long transactionCount) {
}
