package com.supermarket.salesservice.controller.internal;

import com.supermarket.salesservice.dto.report.CashRegisterClosureRow;
import com.supermarket.salesservice.dto.report.PeriodSummaryRow;
import com.supermarket.salesservice.dto.report.ProductSoldRow;
import com.supermarket.salesservice.dto.report.SalesByBranchRow;
import com.supermarket.salesservice.dto.report.SalesByCashierRow;
import com.supermarket.salesservice.dto.report.SalesByProductRow;
import com.supermarket.salesservice.service.business.SalesReportDataService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/reports")
@RequiredArgsConstructor
public class InternalReportController {

    private final SalesReportDataService salesReportDataService;

    @GetMapping("/sales/summary")
    public PeriodSummaryRow getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return salesReportDataService.getPeriodSummary(startDate, endDate, branchId);
    }

    @GetMapping("/sales/by-branch")
    public List<SalesByBranchRow> getByBranch(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return salesReportDataService.getSalesByBranch(startDate, endDate, branchId);
    }

    @GetMapping("/sales/by-product")
    public List<SalesByProductRow> getByProduct(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long productId) {
        return salesReportDataService.getSalesByProduct(startDate, endDate, branchId, productId);
    }

    @GetMapping("/sales/by-cashier")
    public List<SalesByCashierRow> getByCashier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long cashierId) {
        return salesReportDataService.getSalesByCashier(startDate, endDate, branchId, cashierId);
    }

    @GetMapping("/sales/quantity-by-product")
    public List<ProductSoldRow> getQuantityByProduct(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return salesReportDataService.getQuantitySoldByProduct(startDate, endDate, branchId);
    }

    @GetMapping("/cash-registers")
    public List<CashRegisterClosureRow> getCashRegisterClosures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "false") boolean showOnlyDiscrepancies) {
        return salesReportDataService.getCashRegisterClosures(startDate, endDate, branchId, showOnlyDiscrepancies);
    }
}
