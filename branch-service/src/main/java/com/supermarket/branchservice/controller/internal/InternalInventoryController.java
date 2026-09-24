package com.supermarket.branchservice.controller.internal;

import com.supermarket.branchservice.dto.internal.StockLevel;
import com.supermarket.branchservice.dto.internal.StockMovementRequest;
import com.supermarket.branchservice.dto.internal.StockMovementResponse;
import com.supermarket.branchservice.model.idempotency.StockOperationType;
import com.supermarket.branchservice.service.business.InventoryService;
import com.supermarket.branchservice.service.business.StockMovementService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final StockMovementService stockMovementService;
    private final InventoryService inventoryService;

    @PostMapping("/branches/{branchId}/decrease")
    public StockMovementResponse decrease(
            @PathVariable Long branchId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody StockMovementRequest request) {
        return move(branchId, idempotencyKey, StockOperationType.DECREASE, request);
    }

    @PostMapping("/branches/{branchId}/increase")
    public StockMovementResponse increase(
            @PathVariable Long branchId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody StockMovementRequest request) {
        return move(branchId, idempotencyKey, StockOperationType.INCREASE, request);
    }

    @GetMapping("/branches/{branchId}/products/{productId}/stock")
    public Integer getStock(@PathVariable Long branchId, @PathVariable Long productId) {
        return inventoryService.getStockInBranch(branchId, productId);
    }

    @GetMapping("/stock-levels")
    public List<StockLevel> getStockLevels(@RequestParam(value = "branchId", required = false) Long branchId) {
        return inventoryService.getStockLevels(branchId);
    }

    private StockMovementResponse move(Long branchId, String idempotencyKey, StockOperationType operation,
                                       StockMovementRequest request) {
        try {
            return stockMovementService.apply(branchId, idempotencyKey, operation, request);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return stockMovementService.replayOf(branchId, idempotencyKey, operation, request);
        }
    }
}
