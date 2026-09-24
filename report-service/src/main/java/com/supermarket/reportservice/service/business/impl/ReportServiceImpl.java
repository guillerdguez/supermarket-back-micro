package com.supermarket.reportservice.service.business.impl;

import com.supermarket.reportservice.client.CashRegisterClosureRow;
import com.supermarket.reportservice.client.InventoryRemote;
import com.supermarket.reportservice.client.PeriodSummaryRow;
import com.supermarket.reportservice.client.ProductSoldRow;
import com.supermarket.reportservice.client.ProductSummary;
import com.supermarket.reportservice.client.SalesByBranchRow;
import com.supermarket.reportservice.client.SalesByCashierRow;
import com.supermarket.reportservice.client.SalesByProductRow;
import com.supermarket.reportservice.client.SalesReportRemote;
import com.supermarket.reportservice.client.StockLevel;
import com.supermarket.reportservice.dto.cashregister.CashRegisterFilterRequest;
import com.supermarket.reportservice.dto.inventory.InventoryStatusResponse;
import com.supermarket.reportservice.dto.report.CashRegisterReportResponse;
import com.supermarket.reportservice.dto.report.ProductPerformanceDTO;
import com.supermarket.reportservice.dto.report.ReportFilterRequest;
import com.supermarket.reportservice.dto.report.SalesByBranchDTO;
import com.supermarket.reportservice.dto.report.SalesByCashierDTO;
import com.supermarket.reportservice.dto.report.SalesByProductDTO;
import com.supermarket.reportservice.dto.report.SalesComparisonResponse;
import com.supermarket.reportservice.dto.report.SalesSummaryResponse;
import com.supermarket.reportservice.service.business.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {
    private final SalesReportRemote salesReportRemote;
    private final InventoryRemote inventoryRemote;

    @Override
    public SalesSummaryResponse getSalesSummary(ReportFilterRequest filter) {
        PeriodSummaryRow summary = salesReportRemote.summary(
                filter.getStartDate(), resolveEndDate(filter.getEndDate()), filter.getBranchId());
        BigDecimal revenue = revenueOf(summary);
        Long count = countOf(summary);
        return SalesSummaryResponse.builder()
                .totalRevenue(revenue)
                .transactionCount(count)
                .averageTicket(calculateAverage(revenue, count))
                .build();
    }

    @Override
    public List<SalesByBranchDTO> getSalesByBranch(ReportFilterRequest filter) {
        List<SalesByBranchRow> rows = salesReportRemote.byBranch(
                filter.getStartDate(), resolveEndDate(filter.getEndDate()), filter.getBranchId());
        Map<Long, String> currentNames = inventoryRemote.branchNames(idsOf(rows, SalesByBranchRow::branchId));
        return rows.stream()
                .map(row -> SalesByBranchDTO.builder()
                        .branchId(row.branchId())
                        .branchName(currentNames.getOrDefault(row.branchId(), row.branchName()))
                        .totalRevenue(row.totalRevenue())
                        .transactionCount(row.transactionCount())
                        .build())
                .toList();
    }

    @Override
    public List<SalesByProductDTO> getSalesByProduct(ReportFilterRequest filter) {
        List<SalesByProductRow> rows = salesReportRemote.byProduct(
                filter.getStartDate(), resolveEndDate(filter.getEndDate()), filter.getBranchId(), filter.getProductId());
        Map<Long, ProductSummary> products = inventoryRemote.productsById(idsOf(rows, SalesByProductRow::productId));
        return rows.stream()
                .map(row -> {
                    ProductSummary product = products.get(row.productId());
                    return SalesByProductDTO.builder()
                            .productId(row.productId())
                            .productName(product != null ? product.name() : row.productName())
                            .productCategory(product != null ? product.category() : row.productCategory())
                            .totalQuantitySold(row.totalQuantitySold())
                            .totalRevenue(row.totalRevenue())
                            .build();
                })
                .toList();
    }

    @Override
    public List<SalesByCashierDTO> getSalesByCashier(ReportFilterRequest filter) {
        List<SalesByCashierRow> rows = salesReportRemote.byCashier(
                filter.getStartDate(), resolveEndDate(filter.getEndDate()), filter.getBranchId(), filter.getCashierId());
        Map<Long, String> usernames = inventoryRemote.usernames(idsOf(rows, SalesByCashierRow::cashierId));
        return rows.stream()
                .map(row -> {
                    BigDecimal revenue = row.totalRevenue() != null ? row.totalRevenue() : BigDecimal.ZERO;
                    return SalesByCashierDTO.builder()
                            .cashierId(row.cashierId())
                            .cashierUsername(usernames.getOrDefault(row.cashierId(), row.cashierUsername()))
                            .totalRevenue(revenue)
                            .transactionCount(row.transactionCount())
                            .averageTicket(calculateAverage(revenue, row.transactionCount()))
                            .build();
                })
                .toList();
    }

    @Override
    public SalesComparisonResponse getSalesComparison(ReportFilterRequest filter) {
        LocalDate endDate = resolveEndDate(filter.getEndDate());
        LocalDate startDate = filter.getStartDate() != null ? filter.getStartDate() : endDate.minusDays(30);
        long daysDiff = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate previousStart = startDate.minusDays(daysDiff);
        LocalDate previousEnd = startDate.minusDays(1);
        SalesComparisonResponse.PeriodSummary current = buildPeriodSummary(startDate, endDate,
                salesReportRemote.summary(startDate, endDate, filter.getBranchId()));
        SalesComparisonResponse.PeriodSummary previous = buildPeriodSummary(previousStart, previousEnd,
                salesReportRemote.summary(previousStart, previousEnd, filter.getBranchId()));
        BigDecimal growth = previous.getTotalRevenue().compareTo(BigDecimal.ZERO) != 0
                ? current.getTotalRevenue()
                .subtract(previous.getTotalRevenue())
                .divide(previous.getTotalRevenue(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return SalesComparisonResponse.builder()
                .currentPeriod(current)
                .previousPeriod(previous)
                .growthPercentage(growth)
                .build();
    }

    @Override
    public InventoryStatusResponse getInventoryStatus(ReportFilterRequest filter) {
        List<StockLevel> levels = inventoryRemote.stockLevels(filter.getBranchId());
        if (levels.isEmpty()) {
            return InventoryStatusResponse.builder()
                    .totalProducts(0L)
                    .totalUnitsInStock(0L)
                    .totalInventoryValue(BigDecimal.ZERO)
                    .lowStockCount(0L)
                    .outOfStockCount(0L)
                    .build();
        }
        Map<Long, BigDecimal> prices = inventoryRemote.allProducts().stream()
                .filter(product -> product.price() != null)
                .collect(Collectors.toMap(ProductSummary::id, ProductSummary::price, (a, b) -> a));
        BigDecimal totalValue = levels.stream()
                .map(level -> prices.getOrDefault(level.productId(), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(level.stock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return InventoryStatusResponse.builder()
                .totalProducts(levels.stream().map(StockLevel::productId).distinct().count())
                .totalUnitsInStock(levels.stream().mapToLong(StockLevel::stock).sum())
                .totalInventoryValue(totalValue)
                .lowStockCount(levels.stream()
                        .filter(level -> level.stock() <= level.minStock() && level.stock() > 0).count())
                .outOfStockCount(levels.stream().filter(level -> level.stock() == 0).count())
                .build();
    }

    @Override
    public List<ProductPerformanceDTO> getProductPerformance(ReportFilterRequest filter) {
        Map<Long, Long> stockByProduct = inventoryRemote.stockLevels(filter.getBranchId()).stream()
                .collect(Collectors.groupingBy(StockLevel::productId,
                        Collectors.summingLong(level -> level.stock() != null ? level.stock() : 0)));
        Map<Long, Long> soldByProduct = salesReportRemote.quantityByProduct(
                        filter.getStartDate(), resolveEndDate(filter.getEndDate()), filter.getBranchId()).stream()
                .collect(Collectors.toMap(ProductSoldRow::productId, ProductSoldRow::totalSold, Long::sum));

        return inventoryRemote.allProducts().stream()
                .map(product -> {
                    long sold = soldByProduct.getOrDefault(product.id(), 0L);
                    long stock = stockByProduct.getOrDefault(product.id(), 0L);
                    double turnover = stock > 0 ? (double) sold / stock : 0.0;
                    return ProductPerformanceDTO.builder()
                            .productId(product.id())
                            .productName(product.name())
                            .productCategory(product.category())
                            .totalSold(sold)
                            .currentStock((int) stock)
                            .inventoryTurnoverRate(BigDecimal.valueOf(turnover).setScale(2, RoundingMode.HALF_UP))
                            .build();
                })
                .sorted(Comparator.comparing(ProductPerformanceDTO::getTotalSold).reversed())
                .toList();
    }

    @Override
    public CashRegisterReportResponse getCashRegisterReport(CashRegisterFilterRequest filter) {
        List<CashRegisterClosureRow> rows = salesReportRemote.closures(
                filter.getStartDate(), resolveEndDate(filter.getEndDate()), filter.getBranchId(),
                filter.isShowOnlyDiscrepancies());
        Map<Long, String> currentNames = inventoryRemote.branchNames(idsOf(rows, CashRegisterClosureRow::branchId));
        List<CashRegisterReportResponse.ClosureDiscrepancyDTO> discrepancies = rows.stream()
                .map(row -> CashRegisterReportResponse.ClosureDiscrepancyDTO.builder()
                        .registerId(row.registerId())
                        .branchId(row.branchId())
                        .branchName(currentNames.getOrDefault(row.branchId(), row.branchName()))
                        .openingTime(row.openingTime())
                        .closingTime(row.closingTime())
                        .openedBy(row.openedBy())
                        .closedBy(row.closedBy())
                        .expectedAmount(row.expectedAmount())
                        .actualClosingAmount(row.actualClosingAmount())
                        .varianceAmount(row.varianceAmount())
                        .build())
                .toList();
        BigDecimal totalSurplus = discrepancies.stream()
                .map(CashRegisterReportResponse.ClosureDiscrepancyDTO::getVarianceAmount)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShortage = discrepancies.stream()
                .map(CashRegisterReportResponse.ClosureDiscrepancyDTO::getVarianceAmount)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CashRegisterReportResponse.builder()
                .totalClosures((long) discrepancies.size())
                .totalSurplus(totalSurplus)
                .totalShortage(totalShortage)
                .discrepancies(discrepancies)
                .build();
    }

    private <T> Set<Long> idsOf(List<T> rows, Function<T, Long> idExtractor) {
        return rows.stream().map(idExtractor).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private LocalDate resolveEndDate(LocalDate endDate) {
        return endDate != null ? endDate : LocalDate.now();
    }

    private BigDecimal revenueOf(PeriodSummaryRow summary) {
        return summary != null && summary.totalRevenue() != null ? summary.totalRevenue() : BigDecimal.ZERO;
    }

    private Long countOf(PeriodSummaryRow summary) {
        return summary != null && summary.transactionCount() != null ? summary.transactionCount() : 0L;
    }

    private BigDecimal calculateAverage(BigDecimal total, Long count) {
        if (count == null || count == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private SalesComparisonResponse.PeriodSummary buildPeriodSummary(LocalDate start, LocalDate end,
                                                                      PeriodSummaryRow summary) {
        BigDecimal revenue = revenueOf(summary);
        Long count = countOf(summary);
        return SalesComparisonResponse.PeriodSummary.builder()
                .startDate(start)
                .endDate(end)
                .totalRevenue(revenue)
                .transactionCount(count)
                .averageTicket(calculateAverage(revenue, count))
                .build();
    }
}
