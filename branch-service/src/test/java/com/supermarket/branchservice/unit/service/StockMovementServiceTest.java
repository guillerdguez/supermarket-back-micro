package com.supermarket.branchservice.unit.service;

import com.supermarket.branchservice.dto.internal.StockItem;
import com.supermarket.branchservice.dto.internal.StockMovementRequest;
import com.supermarket.branchservice.dto.internal.StockMovementResponse;
import com.supermarket.branchservice.model.idempotency.ProcessedStockOperation;
import com.supermarket.branchservice.model.idempotency.StockOperationType;
import com.supermarket.branchservice.repository.ProcessedStockOperationRepository;
import com.supermarket.branchservice.service.business.InventoryService;
import com.supermarket.branchservice.service.business.StockMovementService;
import com.supermarket.commons.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    private static final String KEY = "sale-7f1c";

    @Mock
    private ProcessedStockOperationRepository processedOperationRepository;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private StockMovementService stockMovementService;

    private final StockMovementRequest request = new StockMovementRequest(List.of(new StockItem(1L, 2)));

    @Test
    @DisplayName("first call with a key should record it and apply the decrease")
    void firstCall_ShouldApply() {
        given(processedOperationRepository.findById(KEY)).willReturn(Optional.empty());

        StockMovementResponse response = stockMovementService.apply(1L, KEY, StockOperationType.DECREASE, request);

        assertThat(response.applied()).isTrue();
        then(processedOperationRepository).should().saveAndFlush(any(ProcessedStockOperation.class));
        then(inventoryService).should().decreaseStockBatch(1L, request.items());
    }

    @Test
    @DisplayName("a retry with the same key should return the previous outcome without touching stock again")
    void retryWithSameKey_ShouldNotApplyTwice() {
        given(processedOperationRepository.findById(KEY)).willReturn(Optional.of(ProcessedStockOperation.builder()
                .idempotencyKey(KEY).branchId(1L).operation(StockOperationType.DECREASE)
                .processedAt(LocalDateTime.now()).build()));

        StockMovementResponse response = stockMovementService.apply(1L, KEY, StockOperationType.DECREASE, request);

        assertThat(response.applied()).isFalse();
        then(inventoryService).should(never()).decreaseStockBatch(anyLong(), anyList());
        then(processedOperationRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reusing a key for a different operation should be rejected")
    void keyReusedForOtherOperation_ShouldBeRejected() {
        given(processedOperationRepository.findById(KEY)).willReturn(Optional.of(ProcessedStockOperation.builder()
                .idempotencyKey(KEY).branchId(1L).operation(StockOperationType.DECREASE)
                .processedAt(LocalDateTime.now()).build()));

        assertThatThrownBy(() -> stockMovementService.apply(1L, KEY, StockOperationType.INCREASE, request))
                .isInstanceOf(InvalidOperationException.class);
        then(inventoryService).should(never()).increaseStockBatch(anyLong(), anyList());
    }

    @Test
    @DisplayName("increase should delegate to the restore logic")
    void increase_ShouldApply() {
        given(processedOperationRepository.findById(KEY)).willReturn(Optional.empty());

        stockMovementService.apply(1L, KEY, StockOperationType.INCREASE, request);

        then(inventoryService).should().increaseStockBatch(1L, request.items());
    }
}
