package com.supermarket.salesservice.service.business;

import com.supermarket.salesservice.dto.report.CashRegisterClosureRow;
import com.supermarket.salesservice.dto.report.PeriodSummaryRow;
import com.supermarket.salesservice.dto.report.ProductSoldRow;
import com.supermarket.salesservice.dto.report.SalesByBranchRow;
import com.supermarket.salesservice.dto.report.SalesByCashierRow;
import com.supermarket.salesservice.dto.report.SalesByProductRow;
import com.supermarket.salesservice.repository.CashRegisterRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportDataService {

    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;

    public PeriodSummaryRow getPeriodSummary(LocalDate startDate, LocalDate endDate, Long branchId) {
        SaleRepository.PeriodSummaryProjection projection =
                saleRepository.findPeriodSummary(startDate, endDate, branchId);
        if (projection == null) {
            return new PeriodSummaryRow(BigDecimal.ZERO, 0L);
        }
        return new PeriodSummaryRow(
                projection.getTotalRevenue() != null ? projection.getTotalRevenue() : BigDecimal.ZERO,
                projection.getTransactionCount() != null ? projection.getTransactionCount() : 0L);
    }

    public List<SalesByBranchRow> getSalesByBranch(LocalDate startDate, LocalDate endDate, Long branchId) {
        return saleRepository.findSalesGroupedByBranch(startDate, endDate, branchId).stream()
                .map(p -> new SalesByBranchRow(p.getBranchId(), p.getBranchName(), p.getTotalRevenue(),
                        p.getTransactionCount()))
                .toList();
    }

    public List<SalesByProductRow> getSalesByProduct(LocalDate startDate, LocalDate endDate, Long branchId,
                                                     Long productId) {
        return saleRepository.findSalesGroupedByProduct(startDate, endDate, branchId, productId).stream()
                .map(p -> new SalesByProductRow(p.getProductId(), p.getProductName(), p.getProductCategory(),
                        p.getTotalQuantitySold(), p.getTotalRevenue()))
                .toList();
    }

    public List<SalesByCashierRow> getSalesByCashier(LocalDate startDate, LocalDate endDate, Long branchId,
                                                     Long cashierId) {
        return saleRepository.findSalesGroupedByCashier(startDate, endDate, branchId, cashierId).stream()
                .map(p -> new SalesByCashierRow(p.getCashierId(), p.getCashierUsername(), p.getTotalRevenue(),
                        p.getTransactionCount()))
                .toList();
    }

    public List<ProductSoldRow> getQuantitySoldByProduct(LocalDate startDate, LocalDate endDate, Long branchId) {
        return saleRepository.findQuantitySoldByProduct(startDate, endDate, branchId).stream()
                .map(p -> new ProductSoldRow(p.getProductId(), p.getTotalSold()))
                .toList();
    }

    public List<CashRegisterClosureRow> getCashRegisterClosures(LocalDate startDate, LocalDate endDate,
                                                                Long branchId, boolean showOnlyDiscrepancies) {
        return cashRegisterRepository.findClosureDiscrepancies(startDate, endDate, branchId, showOnlyDiscrepancies)
                .stream()
                .map(p -> new CashRegisterClosureRow(p.getRegisterId(), p.getBranchId(), p.getBranchName(),
                        p.getOpeningTime(), p.getClosingTime(), p.getOpenedBy(), p.getClosedBy(),
                        p.getExpectedAmount(), p.getActualClosingAmount(), p.getVarianceAmount()))
                .toList();
    }
}
