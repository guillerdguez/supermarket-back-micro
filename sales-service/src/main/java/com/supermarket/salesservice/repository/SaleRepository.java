package com.supermarket.salesservice.repository;

import com.supermarket.salesservice.model.sale.Sale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    boolean existsByBranchId(Long branchId);

    boolean existsByDetailsProductId(Long productId);

    @Override
    @EntityGraph(attributePaths = {"cashRegister"})
    List<Sale> findAll(Sort sort);

    @EntityGraph(attributePaths = {"details", "cashRegister"})
    Optional<Sale> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"cashRegister"})
    List<Sale> findByCreatedById(Long cashierId, Sort sort);

    @Query("""
            SELECT
                SUM(s.total)  as totalRevenue,
                COUNT(s.id)   as transactionCount
            FROM Sale s
            WHERE s.status = 'REGISTERED'
            AND (:startDate IS NULL OR s.date >= :startDate)
            AND (:endDate   IS NULL OR s.date <= :endDate)
            AND (:branchId  IS NULL OR s.branchId = :branchId)
            """)
    PeriodSummaryProjection findPeriodSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("branchId") Long branchId);

    @Query("""
            SELECT
                s.branchId        as branchId,
                MAX(s.branchName) as branchName,
                SUM(s.total)      as totalRevenue,
                COUNT(s.id)       as transactionCount
            FROM Sale s
            WHERE s.status = 'REGISTERED'
            AND (:startDate IS NULL OR s.date >= :startDate)
            AND (:endDate   IS NULL OR s.date <= :endDate)
            AND (:branchId  IS NULL OR s.branchId = :branchId)
            GROUP BY s.branchId
            ORDER BY totalRevenue DESC
            """)
    List<SalesByBranchProjection> findSalesGroupedByBranch(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("branchId") Long branchId);

    @Query("""
            SELECT
                sd.productId              as productId,
                MAX(sd.productName)       as productName,
                MAX(sd.productCategory)   as productCategory,
                SUM(sd.quantity)          as totalQuantitySold,
                SUM(sd.price * sd.quantity) as totalRevenue
            FROM SaleDetail sd
            JOIN sd.sale s
            WHERE s.status = 'REGISTERED'
            AND (:startDate IS NULL OR s.date >= :startDate)
            AND (:endDate   IS NULL OR s.date <= :endDate)
            AND (:branchId  IS NULL OR s.branchId = :branchId)
            AND (:productId IS NULL OR sd.productId = :productId)
            GROUP BY sd.productId
            ORDER BY totalRevenue DESC
            """)
    List<SalesByProductProjection> findSalesGroupedByProduct(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("branchId") Long branchId,
            @Param("productId") Long productId);

    @Query("""
            SELECT
                s.createdById             as cashierId,
                MAX(s.createdByUsername)  as cashierUsername,
                SUM(s.total)              as totalRevenue,
                COUNT(s.id)               as transactionCount
            FROM Sale s
            WHERE s.status = 'REGISTERED'
            AND s.createdById IS NOT NULL
            AND (:startDate  IS NULL OR s.date >= :startDate)
            AND (:endDate    IS NULL OR s.date <= :endDate)
            AND (:branchId   IS NULL OR s.branchId = :branchId)
            AND (:cashierId  IS NULL OR s.createdById = :cashierId)
            GROUP BY s.createdById
            ORDER BY totalRevenue DESC
            """)
    List<SalesByCashierProjection> findSalesGroupedByCashier(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("branchId") Long branchId,
            @Param("cashierId") Long cashierId);

    @Query("""
            SELECT
                sd.productId      as productId,
                SUM(sd.quantity)  as totalSold
            FROM SaleDetail sd
            JOIN sd.sale s
            WHERE s.status = 'REGISTERED'
            AND (:startDate IS NULL OR s.date >= :startDate)
            AND (:endDate   IS NULL OR s.date <= :endDate)
            AND (:branchId  IS NULL OR s.branchId = :branchId)
            GROUP BY sd.productId
            """)
    List<ProductSoldProjection> findQuantitySoldByProduct(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(s.total), 0) FROM Sale s WHERE s.cashRegister.id = :cashRegisterId AND s.status = 'REGISTERED'")
    BigDecimal sumTotalByCashRegisterId(@Param("cashRegisterId") Long cashRegisterId);

    interface PeriodSummaryProjection {
        BigDecimal getTotalRevenue();

        Long getTransactionCount();
    }

    interface SalesByBranchProjection {
        Long getBranchId();

        String getBranchName();

        BigDecimal getTotalRevenue();

        Long getTransactionCount();
    }

    interface SalesByProductProjection {
        Long getProductId();

        String getProductName();

        String getProductCategory();

        Long getTotalQuantitySold();

        BigDecimal getTotalRevenue();
    }

    interface SalesByCashierProjection {
        Long getCashierId();

        String getCashierUsername();

        BigDecimal getTotalRevenue();

        Long getTransactionCount();
    }

    interface ProductSoldProjection {
        Long getProductId();

        Long getTotalSold();
    }
}
