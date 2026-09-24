package com.supermarket.salesservice.integration;

import com.supermarket.salesservice.dto.report.CashRegisterClosureRow;
import com.supermarket.salesservice.dto.report.PeriodSummaryRow;
import com.supermarket.salesservice.dto.report.ProductSoldRow;
import com.supermarket.salesservice.dto.report.SalesByBranchRow;
import com.supermarket.salesservice.dto.report.SalesByCashierRow;
import com.supermarket.salesservice.service.business.SalesReportDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=",
        "spring.sql.init.mode=always"
})
@ActiveProfiles("test")
@Testcontainers
class SalesReportMySqlIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("salesdb");

    @Autowired
    private SalesReportDataService salesReportDataService;

    @Test
    @DisplayName("period summary over the real seed matches the monolith totals for registered sales")
    void periodSummary_ShouldMatchSeed() {
        PeriodSummaryRow summary = salesReportDataService.getPeriodSummary(null, null, null);

        assertThat(summary.transactionCount()).isEqualTo(394L);
        assertThat(summary.totalRevenue()).isEqualByComparingTo("6985.37");
    }

    @Test
    @DisplayName("branch filter and grouping by branch work on MySQL")
    void salesByBranch_ShouldGroupOnMySql() {
        List<SalesByBranchRow> rows = salesReportDataService.getSalesByBranch(null, null, 1L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.branchName()).isEqualTo("Sucursal Centro");
            assertThat(row.transactionCount()).isEqualTo(74L);
            assertThat(row.totalRevenue()).isEqualByComparingTo("1350.59");
        });
    }

    @Test
    @DisplayName("closure report with date filters uses CAST(... AS date) correctly on MySQL")
    void cashRegisterClosures_ShouldRunWithDateFilters() {
        List<CashRegisterClosureRow> all = salesReportDataService.getCashRegisterClosures(null, null, null, false);
        List<CashRegisterClosureRow> onlyDiscrepancies =
                salesReportDataService.getCashRegisterClosures(null, null, null, true);
        List<CashRegisterClosureRow> firstDay = salesReportDataService.getCashRegisterClosures(
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15), null, false);

        assertThat(all).hasSize(100);
        assertThat(onlyDiscrepancies).hasSize(92);
        assertThat(firstDay).hasSize(5).allSatisfy(row ->
                assertThat(row.closingTime().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 15)));
        assertThat(onlyDiscrepancies).allSatisfy(row ->
                assertThat(row.varianceAmount()).isNotEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("quantity sold by product only counts registered sales")
    void quantitySoldByProduct_ShouldCountRegisteredSales() {
        List<ProductSoldRow> rows = salesReportDataService.getQuantitySoldByProduct(null, null, null);

        assertThat(rows).hasSize(68);
        assertThat(rows).filteredOn(row -> row.productId().equals(1L))
                .singleElement().satisfies(row -> assertThat(row.totalSold()).isEqualTo(49L));
    }

    @Test
    @DisplayName("sales by cashier keep the username frozen at sale time")
    void salesByCashier_ShouldUseSnapshotUsername() {
        List<SalesByCashierRow> rows = salesReportDataService.getSalesByCashier(null, null, null, 3L);

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.cashierUsername()).isEqualTo("cashier1"));
    }
}
