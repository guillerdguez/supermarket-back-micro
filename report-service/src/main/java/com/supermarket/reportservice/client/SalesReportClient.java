package com.supermarket.reportservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "sales-service", contextId = "salesReportClient", path = "/internal/reports")
public interface SalesReportClient {

    @GetMapping("/sales/summary")
    PeriodSummaryRow getSummary(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "branchId", required = false) Long branchId);

    @GetMapping("/sales/by-branch")
    List<SalesByBranchRow> getByBranch(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "branchId", required = false) Long branchId);

    @GetMapping("/sales/by-product")
    List<SalesByProductRow> getByProduct(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "productId", required = false) Long productId);

    @GetMapping("/sales/by-cashier")
    List<SalesByCashierRow> getByCashier(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "cashierId", required = false) Long cashierId);

    @GetMapping("/sales/quantity-by-product")
    List<ProductSoldRow> getQuantityByProduct(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "branchId", required = false) Long branchId);

    @GetMapping("/cash-registers")
    List<CashRegisterClosureRow> getCashRegisterClosures(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "showOnlyDiscrepancies") boolean showOnlyDiscrepancies);
}
