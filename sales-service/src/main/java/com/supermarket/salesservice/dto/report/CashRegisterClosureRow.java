package com.supermarket.salesservice.dto.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashRegisterClosureRow(
        Long registerId,
        Long branchId,
        String branchName,
        LocalDateTime openingTime,
        LocalDateTime closingTime,
        String openedBy,
        String closedBy,
        BigDecimal expectedAmount,
        BigDecimal actualClosingAmount,
        BigDecimal varianceAmount) {
}
