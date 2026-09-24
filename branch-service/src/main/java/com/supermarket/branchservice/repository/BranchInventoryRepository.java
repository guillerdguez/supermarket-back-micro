package com.supermarket.branchservice.repository;


import com.supermarket.branchservice.model.branch.BranchInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BranchInventoryRepository extends JpaRepository<BranchInventory, Long> {
    Optional<BranchInventory> findByBranchIdAndProductId(Long branchId, Long productId);

    List<BranchInventory> findByBranchId(Long branchId);

    boolean existsByBranchIdAndStockGreaterThan(Long branchId, Integer stock);

    @Modifying
    @Query("DELETE FROM BranchInventory bi WHERE bi.branch.id = :branchId")
    void deleteByBranchId(@Param("branchId") Long branchId);

    List<BranchInventory> findByProductId(Long productId);

    @Query("SELECT bi FROM BranchInventory bi WHERE bi.branch.id = :branchId AND bi.stock <= bi.minStock")
    List<BranchInventory> findLowStockByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT bi FROM BranchInventory bi WHERE bi.stock <= bi.minStock")
    List<BranchInventory> findLowStockGlobal();

    List<BranchInventory> findByBranchIdAndProductIdIn(Long branchId, Set<Long> productIds);

    @Modifying
    @Query("DELETE FROM BranchInventory bi WHERE bi.productId = :productId")
    int deleteByProductId(@Param("productId") Long productId);

    boolean existsByBranchIdAndProductId(Long branchId, Long productId);

    @Query("SELECT bi FROM BranchInventory bi WHERE (:branchId IS NULL OR bi.branch.id = :branchId)")
    List<BranchInventory> findAllForBranchOrGlobal(@Param("branchId") Long branchId);
}
