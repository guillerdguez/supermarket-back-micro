package com.supermarket.branchservice.service.business;

import com.supermarket.branchservice.dto.internal.StockMovementRequest;
import com.supermarket.branchservice.dto.internal.StockMovementResponse;
import com.supermarket.branchservice.model.idempotency.ProcessedStockOperation;
import com.supermarket.branchservice.model.idempotency.StockOperationType;
import com.supermarket.branchservice.repository.ProcessedStockOperationRepository;
import com.supermarket.commons.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockMovementService {

    private final ProcessedStockOperationRepository processedOperationRepository;
    private final InventoryService inventoryService;

    @Transactional
    public StockMovementResponse apply(Long branchId, String idempotencyKey, StockOperationType operation,
                                       StockMovementRequest request) {
        Optional<ProcessedStockOperation> previous = processedOperationRepository.findById(idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), branchId, operation, request);
        }
        processedOperationRepository.saveAndFlush(ProcessedStockOperation.builder()
                .idempotencyKey(idempotencyKey)
                .branchId(branchId)
                .operation(operation)
                .processedAt(LocalDateTime.now())
                .build());
        if (operation == StockOperationType.DECREASE) {
            inventoryService.decreaseStockBatch(branchId, request.items());
        } else {
            inventoryService.increaseStockBatch(branchId, request.items());
        }
        log.info("Applied {} on branch {} with idempotency key {}", operation, branchId, idempotencyKey);
        return new StockMovementResponse(branchId, idempotencyKey, operation.name(), true, request.items());
    }

    @Transactional(readOnly = true)
    public StockMovementResponse replayOf(Long branchId, String idempotencyKey, StockOperationType operation,
                                          StockMovementRequest request) {
        return processedOperationRepository.findById(idempotencyKey)
                .map(previous -> replay(previous, branchId, operation, request))
                .orElseThrow(() -> new InvalidOperationException(
                        "Stock operation " + idempotencyKey + " could not be applied"));
    }

    private StockMovementResponse replay(ProcessedStockOperation previous, Long branchId,
                                         StockOperationType operation, StockMovementRequest request) {
        if (!previous.getBranchId().equals(branchId) || previous.getOperation() != operation) {
            throw new InvalidOperationException(
                    "Idempotency key " + previous.getIdempotencyKey() + " was already used for a different operation");
        }
        log.info("Replayed {} on branch {} for idempotency key {} without applying it again",
                operation, branchId, previous.getIdempotencyKey());
        return new StockMovementResponse(branchId, previous.getIdempotencyKey(), operation.name(), false,
                request.items());
    }
}
