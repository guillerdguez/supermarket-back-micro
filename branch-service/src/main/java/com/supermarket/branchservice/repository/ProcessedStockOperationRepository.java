package com.supermarket.branchservice.repository;

import com.supermarket.branchservice.model.idempotency.ProcessedStockOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedStockOperationRepository extends JpaRepository<ProcessedStockOperation, String> {
}
