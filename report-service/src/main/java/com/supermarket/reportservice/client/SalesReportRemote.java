package com.supermarket.reportservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesReportRemote {

    static final String SALES_SERVICE = "sales-service";

    private final SalesReportClient client;

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "summaryFallback")
    @Retry(name = SALES_SERVICE)
    public PeriodSummaryRow summary(LocalDate startDate, LocalDate endDate, Long branchId) {
        return client.getSummary(startDate, endDate, branchId);
    }

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "byBranchFallback")
    @Retry(name = SALES_SERVICE)
    public List<SalesByBranchRow> byBranch(LocalDate startDate, LocalDate endDate, Long branchId) {
        return client.getByBranch(startDate, endDate, branchId);
    }

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "byProductFallback")
    @Retry(name = SALES_SERVICE)
    public List<SalesByProductRow> byProduct(LocalDate startDate, LocalDate endDate, Long branchId, Long productId) {
        return client.getByProduct(startDate, endDate, branchId, productId);
    }

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "byCashierFallback")
    @Retry(name = SALES_SERVICE)
    public List<SalesByCashierRow> byCashier(LocalDate startDate, LocalDate endDate, Long branchId, Long cashierId) {
        return client.getByCashier(startDate, endDate, branchId, cashierId);
    }

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "quantityByProductFallback")
    @Retry(name = SALES_SERVICE)
    public List<ProductSoldRow> quantityByProduct(LocalDate startDate, LocalDate endDate, Long branchId) {
        return client.getQuantityByProduct(startDate, endDate, branchId);
    }

    @CircuitBreaker(name = SALES_SERVICE, fallbackMethod = "closuresFallback")
    @Retry(name = SALES_SERVICE)
    public List<CashRegisterClosureRow> closures(LocalDate startDate, LocalDate endDate, Long branchId,
                                                 boolean showOnlyDiscrepancies) {
        return client.getCashRegisterClosures(startDate, endDate, branchId, showOnlyDiscrepancies);
    }

    private PeriodSummaryRow summaryFallback(LocalDate startDate, LocalDate endDate, Long branchId,
                                             Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }

    private List<SalesByBranchRow> byBranchFallback(LocalDate startDate, LocalDate endDate, Long branchId,
                                                    Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }

    private List<SalesByProductRow> byProductFallback(LocalDate startDate, LocalDate endDate, Long branchId, Long productId,
                                                      Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }

    private List<SalesByCashierRow> byCashierFallback(LocalDate startDate, LocalDate endDate, Long branchId, Long cashierId,
                                                      Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }

    private List<ProductSoldRow> quantityByProductFallback(LocalDate startDate, LocalDate endDate, Long branchId,
                                                           Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }

    private List<CashRegisterClosureRow> closuresFallback(LocalDate startDate, LocalDate endDate, Long branchId, boolean showOnlyDiscrepancies,
                                                          Throwable throwable) {
        throw RemoteFailures.propagate(SALES_SERVICE, throwable);
    }
}
