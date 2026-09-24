package com.supermarket.reportservice.service.business;

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

import java.util.List;

public interface ReportService {
    SalesSummaryResponse getSalesSummary(ReportFilterRequest filter);

    List<SalesByBranchDTO> getSalesByBranch(ReportFilterRequest filter);

    List<SalesByProductDTO> getSalesByProduct(ReportFilterRequest filter);

    List<SalesByCashierDTO> getSalesByCashier(ReportFilterRequest filter);

    SalesComparisonResponse getSalesComparison(ReportFilterRequest filter);

    InventoryStatusResponse getInventoryStatus(ReportFilterRequest filter);

    List<ProductPerformanceDTO> getProductPerformance(ReportFilterRequest filter);

    CashRegisterReportResponse getCashRegisterReport(CashRegisterFilterRequest filter);
}