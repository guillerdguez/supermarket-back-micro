package com.supermarket.reportservice.unit.service;

import com.supermarket.commons.exception.RemoteServiceException;
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
import com.supermarket.reportservice.service.business.impl.ReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private SalesReportRemote salesReportRemote;
    @Mock
    private InventoryRemote inventoryRemote;
    @InjectMocks
    private ReportServiceImpl reportService;

    private final ReportFilterRequest filter = ReportFilterRequest.builder()
            .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31)).build();

    @Test
    @DisplayName("sales summary - should compute the average ticket from the sales-service aggregate")
    void salesSummary_ShouldComputeAverage() {
        given(salesReportRemote.summary(filter.getStartDate(), filter.getEndDate(), null))
                .willReturn(new PeriodSummaryRow(new BigDecimal("100.00"), 8L));

        SalesSummaryResponse response = reportService.getSalesSummary(filter);

        assertThat(response.getTotalRevenue()).isEqualByComparingTo("100.00");
        assertThat(response.getTransactionCount()).isEqualTo(8L);
        assertThat(response.getAverageTicket()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("sales summary - should return zeros when there are no sales")
    void salesSummary_WhenNoSales_ShouldReturnZeros() {
        given(salesReportRemote.summary(any(), any(), any())).willReturn(new PeriodSummaryRow(null, 0L));

        SalesSummaryResponse response = reportService.getSalesSummary(filter);

        assertThat(response.getTotalRevenue()).isEqualByComparingTo("0");
        assertThat(response.getAverageTicket()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sales summary - should fail with 503 semantics when sales-service is down")
    void salesSummary_WhenSalesDown_ShouldPropagate() {
        given(salesReportRemote.summary(any(), any(), any()))
                .willThrow(new RemoteServiceException("sales-service", "sales-service is temporarily unavailable"));

        assertThatThrownBy(() -> reportService.getSalesSummary(filter)).isInstanceOf(RemoteServiceException.class);
    }

    @Test
    @DisplayName("sales by branch - should use the current branch name from branch-service")
    void salesByBranch_ShouldUseCurrentNames() {
        given(salesReportRemote.byBranch(any(), any(), isNull())).willReturn(List.of(
                new SalesByBranchRow(1L, "Old Name", new BigDecimal("50.00"), 3L)));
        given(inventoryRemote.branchNames(Set.of(1L))).willReturn(Map.of(1L, "Sucursal Centro"));

        List<SalesByBranchDTO> rows = reportService.getSalesByBranch(filter);

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.getBranchName()).isEqualTo("Sucursal Centro"));
    }

    @Test
    @DisplayName("sales by branch - should fall back to the name frozen in the sale when branch-service is down")
    void salesByBranch_WhenBranchDown_ShouldUseSnapshot() {
        given(salesReportRemote.byBranch(any(), any(), isNull())).willReturn(List.of(
                new SalesByBranchRow(1L, "Sucursal Centro", new BigDecimal("50.00"), 3L)));
        given(inventoryRemote.branchNames(any())).willReturn(Map.of());

        assertThat(reportService.getSalesByBranch(filter)).singleElement()
                .satisfies(row -> assertThat(row.getBranchName()).isEqualTo("Sucursal Centro"));
    }

    @Test
    @DisplayName("sales by product - should enrich with catalog data or keep the snapshot")
    void salesByProduct_ShouldEnrich() {
        given(salesReportRemote.byProduct(any(), any(), isNull(), isNull())).willReturn(List.of(
                new SalesByProductRow(1L, "Leche", "Lacteos", 5L, new BigDecimal("5.75")),
                new SalesByProductRow(2L, "Pan", "Panaderia", 1L, new BigDecimal("0.90"))));
        given(inventoryRemote.productsById(Set.of(1L, 2L))).willReturn(Map.of(
                1L, new ProductSummary(1L, "Leche Entera 1L", "8410000000001", "Lacteos", new BigDecimal("1.15"))));

        List<SalesByProductDTO> rows = reportService.getSalesByProduct(filter);

        assertThat(rows).extracting(SalesByProductDTO::getProductName).containsExactly("Leche Entera 1L", "Pan");
    }

    @Test
    @DisplayName("sales by cashier - should compute the average ticket and use current usernames")
    void salesByCashier_ShouldComputeAverage() {
        given(salesReportRemote.byCashier(any(), any(), isNull(), isNull())).willReturn(List.of(
                new SalesByCashierRow(3L, "cashier1", new BigDecimal("30.00"), 4L)));
        given(inventoryRemote.usernames(Set.of(3L))).willReturn(Map.of(3L, "cashier-renamed"));

        List<SalesByCashierDTO> rows = reportService.getSalesByCashier(filter);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getCashierUsername()).isEqualTo("cashier-renamed");
            assertThat(row.getAverageTicket()).isEqualByComparingTo("7.50");
        });
    }

    @Test
    @DisplayName("sales comparison - should compare against the previous period of the same length")
    void salesComparison_ShouldComputeGrowth() {
        given(salesReportRemote.summary(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null))
                .willReturn(new PeriodSummaryRow(new BigDecimal("150.00"), 10L));
        given(salesReportRemote.summary(LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30), null))
                .willReturn(new PeriodSummaryRow(new BigDecimal("100.00"), 8L));

        SalesComparisonResponse response = reportService.getSalesComparison(filter);

        assertThat(response.getGrowthPercentage()).isEqualByComparingTo("50.00");
        assertThat(response.getPreviousPeriod().getStartDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("inventory status - should combine stock levels from branch-service with catalog prices")
    void inventoryStatus_ShouldCombineStockAndPrices() {
        given(inventoryRemote.stockLevels(null)).willReturn(List.of(
                new StockLevel(1L, 1L, 10, 5),
                new StockLevel(2L, 1L, 3, 5),
                new StockLevel(1L, 2L, 0, 5)));
        given(inventoryRemote.allProducts()).willReturn(List.of(
                new ProductSummary(1L, "Leche", null, "Lacteos", new BigDecimal("1.15")),
                new ProductSummary(2L, "Pan", null, "Panaderia", new BigDecimal("0.90"))));

        InventoryStatusResponse response = reportService.getInventoryStatus(filter);

        assertThat(response.getTotalProducts()).isEqualTo(2L);
        assertThat(response.getTotalUnitsInStock()).isEqualTo(13L);
        assertThat(response.getTotalInventoryValue()).isEqualByComparingTo("14.95");
        assertThat(response.getLowStockCount()).isEqualTo(1L);
        assertThat(response.getOutOfStockCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("inventory status - should return zeros when there is no inventory")
    void inventoryStatus_WhenEmpty_ShouldReturnZeros() {
        given(inventoryRemote.stockLevels(null)).willReturn(List.of());

        InventoryStatusResponse response = reportService.getInventoryStatus(filter);

        assertThat(response.getTotalProducts()).isZero();
        assertThat(response.getTotalInventoryValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("product performance - should include products without sales and sort by quantity sold")
    void productPerformance_ShouldComputeTurnover() {
        given(inventoryRemote.stockLevels(null)).willReturn(List.of(
                new StockLevel(1L, 1L, 20, 5), new StockLevel(1L, 2L, 10, 5)));
        given(salesReportRemote.quantityByProduct(any(), any(), isNull()))
                .willReturn(List.of(new ProductSoldRow(2L, 5L)));
        given(inventoryRemote.allProducts()).willReturn(List.of(
                new ProductSummary(1L, "Leche", null, "Lacteos", new BigDecimal("1.15")),
                new ProductSummary(2L, "Pan", null, "Panaderia", new BigDecimal("0.90"))));

        List<ProductPerformanceDTO> rows = reportService.getProductPerformance(filter);

        assertThat(rows).extracting(ProductPerformanceDTO::getProductName).containsExactly("Pan", "Leche");
        assertThat(rows.get(0).getInventoryTurnoverRate()).isEqualByComparingTo("0.50");
        assertThat(rows.get(1).getTotalSold()).isZero();
    }

    @Test
    @DisplayName("cash registers - should total surplus and shortage from sales-service closures")
    void cashRegisters_ShouldTotalVariances() {
        LocalDateTime now = LocalDateTime.now();
        given(salesReportRemote.closures(any(), any(), isNull(), eq(false))).willReturn(List.of(
                new CashRegisterClosureRow(1L, 1L, "Sucursal Centro", now, now, "cashier1", "admin",
                        new BigDecimal("100"), new BigDecimal("102.10"), new BigDecimal("2.10")),
                new CashRegisterClosureRow(2L, 1L, "Sucursal Centro", now, now, "cashier1", "admin",
                        new BigDecimal("100"), new BigDecimal("99.93"), new BigDecimal("-0.07"))));
        given(inventoryRemote.branchNames(Set.of(1L))).willReturn(Map.of());

        CashRegisterReportResponse response = reportService.getCashRegisterReport(CashRegisterFilterRequest.builder()
                .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 7, 31)).build());

        assertThat(response.getTotalClosures()).isEqualTo(2L);
        assertThat(response.getTotalSurplus()).isEqualByComparingTo("2.10");
        assertThat(response.getTotalShortage()).isEqualByComparingTo("-0.07");
    }
}
