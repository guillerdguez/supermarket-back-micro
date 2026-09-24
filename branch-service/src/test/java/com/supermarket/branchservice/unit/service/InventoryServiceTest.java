package com.supermarket.branchservice.unit.service;

import com.supermarket.branchservice.client.CatalogLookupService;
import com.supermarket.branchservice.client.ProductSummary;
import com.supermarket.branchservice.dto.internal.StockItem;
import com.supermarket.branchservice.dto.internal.StockLevel;
import com.supermarket.branchservice.dto.inventory.BranchInventoryResponse;
import com.supermarket.branchservice.dto.inventory.LowStockAlertResponse;
import com.supermarket.branchservice.dto.inventory.StockAdjustmentRequest;
import com.supermarket.branchservice.dto.inventory.StockUpdateRequest;
import com.supermarket.branchservice.dto.inventory.TotalStockResponse;
import com.supermarket.branchservice.event.StockEventPublisher;
import com.supermarket.branchservice.mapper.BranchInventoryMapper;
import com.supermarket.branchservice.model.branch.Branch;
import com.supermarket.branchservice.model.branch.BranchInventory;
import com.supermarket.branchservice.repository.BranchInventoryRepository;
import com.supermarket.branchservice.repository.BranchRepository;
import com.supermarket.branchservice.service.business.impl.InventoryServiceImpl;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.supermarket.branchservice.fixtures.branch.BranchFixtures.defaultBranch;
import static com.supermarket.branchservice.fixtures.product.ProductFixtures.defaultProduct;
import static com.supermarket.branchservice.fixtures.product.ProductFixtures.productWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock
    private BranchInventoryRepository branchInventoryRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private CatalogLookupService catalogLookupService;
    @Mock
    private StockEventPublisher stockEventPublisher;

    private InventoryServiceImpl inventoryService;
    private Branch branch;
    private ProductSummary product;
    private BranchInventory inventory;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(branchInventoryRepository, branchRepository,
                new BranchInventoryMapper(), catalogLookupService, stockEventPublisher);
        branch = defaultBranch();
        product = defaultProduct();
        inventory = inventoryRow(product.id(), 50, 5);
        lenient().when(catalogLookupService.productsById(any())).thenReturn(Map.of(product.id(), product));
    }

    private BranchInventory inventoryRow(Long productId, int stock, int minStock) {
        return BranchInventory.builder()
                .id(100L + productId)
                .branch(branch)
                .productId(productId)
                .stock(stock)
                .minStock(minStock)
                .lastRestockDate(LocalDateTime.now().minusDays(1))
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("Stock queries")
    class StockQueries {
        @Test
        @DisplayName("getStockInBranch - should return stock when inventory exists")
        void getStockInBranch_WhenExists_ShouldReturnStock() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));
            assertThat(inventoryService.getStockInBranch(1L, 1L)).isEqualTo(50);
        }

        @Test
        @DisplayName("getStockInBranch - should return 0 when inventory not found")
        void getStockInBranch_WhenNotFound_ShouldReturnZero() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 99L)).willReturn(Optional.empty());
            assertThat(inventoryService.getStockInBranch(1L, 99L)).isZero();
        }

        @Test
        @DisplayName("getMinStockInBranch - should return minStock when inventory exists")
        void getMinStockInBranch_WhenExists_ShouldReturnMinStock() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));
            assertThat(inventoryService.getMinStockInBranch(1L, 1L)).isEqualTo(5);
        }

        @Test
        @DisplayName("getMinStockInBranch - should return 0 when inventory not found")
        void getMinStockInBranch_WhenNotFound_ShouldReturnZero() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 99L)).willReturn(Optional.empty());
            assertThat(inventoryService.getMinStockInBranch(1L, 99L)).isZero();
        }

        @Test
        @DisplayName("getLowStockInBranch - should return alerts enriched with the product name from catalog")
        void getLowStockInBranch_ShouldReturnList() {
            given(branchInventoryRepository.findLowStockByBranchId(1L)).willReturn(List.of(inventoryRow(1L, 3, 5)));

            List<LowStockAlertResponse> result = inventoryService.getLowStockInBranch(1L);

            assertThat(result).hasSize(1);
            LowStockAlertResponse alert = result.get(0);
            assertThat(alert.getBranchId()).isEqualTo(1L);
            assertThat(alert.getProductId()).isEqualTo(1L);
            assertThat(alert.getProductName()).isEqualTo("Premium Rice");
            assertThat(alert.getCurrentStock()).isEqualTo(3);
            assertThat(alert.getMinStock()).isEqualTo(5);
        }

        @Test
        @DisplayName("getLowStockGlobal - should degrade to a placeholder name when catalog is unavailable")
        void getLowStockGlobal_WhenCatalogDown_ShouldUsePlaceholderName() {
            given(branchInventoryRepository.findLowStockGlobal()).willReturn(List.of(inventoryRow(7L, 1, 5)));
            given(catalogLookupService.productsById(any())).willReturn(Map.of());

            List<LowStockAlertResponse> result = inventoryService.getLowStockGlobal();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductName()).isEqualTo("Product #7");
        }

        @Test
        @DisplayName("getBranchInventory - should throw when branch does not exist")
        void getBranchInventory_WhenBranchMissing_ShouldThrow() {
            given(branchRepository.existsById(9L)).willReturn(false);
            assertThatThrownBy(() -> inventoryService.getBranchInventory(9L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getBranchInventory - should map rows with product data from catalog")
        void getBranchInventory_ShouldReturnRows() {
            given(branchRepository.existsById(1L)).willReturn(true);
            given(branchInventoryRepository.findByBranchId(1L)).willReturn(List.of(inventory));

            List<BranchInventoryResponse> result = inventoryService.getBranchInventory(1L);

            assertThat(result).singleElement().satisfies(row -> {
                assertThat(row.getProductName()).isEqualTo("Premium Rice");
                assertThat(row.getProductCategory()).isEqualTo("Food");
                assertThat(row.getStock()).isEqualTo(50);
            });
        }

        @Test
        @DisplayName("getTotalStockByProduct - should return sum of stock across branches")
        void getTotalStockByProduct_ShouldReturnSum() {
            given(catalogLookupService.requireProduct(1L)).willReturn(product);
            given(branchInventoryRepository.findByProductId(1L))
                    .willReturn(List.of(inventoryRow(1L, 10, 5), inventoryRow(1L, 25, 5)));

            TotalStockResponse result = inventoryService.getTotalStockByProduct(1L);

            assertThat(result.getTotalStock()).isEqualTo(35L);
            assertThat(result.getProductName()).isEqualTo("Premium Rice");
        }

        @Test
        @DisplayName("getTotalStockByProduct - should throw when product not found")
        void getTotalStockByProduct_WhenProductMissing_ShouldThrow() {
            given(catalogLookupService.requireProduct(99L))
                    .willThrow(new ResourceNotFoundException("Product not found with ID: 99"));
            assertThatThrownBy(() -> inventoryService.getTotalStockByProduct(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getStockLevels - should expose raw stock levels for reporting")
        void getStockLevels_ShouldReturnLevels() {
            given(branchInventoryRepository.findAllForBranchOrGlobal(null)).willReturn(List.of(inventory));

            List<StockLevel> levels = inventoryService.getStockLevels(null);

            assertThat(levels).containsExactly(new StockLevel(1L, 1L, 50, 5));
        }
    }

    @Nested
    @DisplayName("Batch stock movements")
    class BatchOperations {
        private BranchInventory second;

        @BeforeEach
        void setUpSecond() {
            second = inventoryRow(2L, 10, 5);
            lenient().when(catalogLookupService.productsById(any()))
                    .thenReturn(Map.of(1L, product, 2L, productWithId(2L)));
        }

        @Test
        @DisplayName("decreaseStockBatch - should reduce stock for all products, grouping repeated lines")
        void decrease_ShouldReduceAll() {
            given(branchInventoryRepository.findByBranchIdAndProductIdIn(1L, Set.of(1L, 2L)))
                    .willReturn(List.of(inventory, second));
            given(branchInventoryRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

            inventoryService.decreaseStockBatch(1L, List.of(
                    new StockItem(1L, 5), new StockItem(1L, 5), new StockItem(2L, 3)));

            assertThat(inventory.getStock()).isEqualTo(40);
            assertThat(second.getStock()).isEqualTo(7);
        }

        @Test
        @DisplayName("decreaseStockBatch - should publish stock.low when an item falls to its minimum")
        void decrease_WhenFallingBelowMinimum_ShouldPublishLowStock() {
            given(branchInventoryRepository.findByBranchIdAndProductIdIn(1L, Set.of(2L))).willReturn(List.of(second));
            given(branchInventoryRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

            inventoryService.decreaseStockBatch(1L, List.of(new StockItem(2L, 6)));

            then(stockEventPublisher).should().stockLow(second, "Premium Rice");
        }

        @Test
        @DisplayName("decreaseStockBatch - should throw when details list is empty")
        void decrease_WhenEmpty_ShouldThrow() {
            assertThatThrownBy(() -> inventoryService.decreaseStockBatch(1L, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("decreaseStockBatch - should throw when productId is null")
        void decrease_WhenProductIdNull_ShouldThrow() {
            assertThatThrownBy(() -> inventoryService.decreaseStockBatch(1L, List.of(new StockItem(null, 1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not have a productId");
        }

        @Test
        @DisplayName("decreaseStockBatch - should throw when quantity is invalid")
        void decrease_WhenQuantityInvalid_ShouldThrow() {
            assertThatThrownBy(() -> inventoryService.decreaseStockBatch(1L, List.of(new StockItem(1L, 0))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid quantity");
        }

        @Test
        @DisplayName("decreaseStockBatch - should throw when some products do not exist in the branch")
        void decrease_WhenProductMissingInBranch_ShouldThrow() {
            given(branchInventoryRepository.findByBranchIdAndProductIdIn(1L, Set.of(1L, 3L)))
                    .willReturn(List.of(inventory));

            assertThatThrownBy(() -> inventoryService.decreaseStockBatch(1L,
                    List.of(new StockItem(1L, 1), new StockItem(3L, 1))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("[3]");
            then(branchInventoryRepository).should(never()).saveAll(anyList());
        }

        @Test
        @DisplayName("decreaseStockBatch - should throw and change nothing when any product lacks stock")
        void decrease_WhenInsufficient_ShouldThrowAndNotSave() {
            given(branchInventoryRepository.findByBranchIdAndProductIdIn(1L, Set.of(1L, 2L)))
                    .willReturn(List.of(inventory, second));

            assertThatThrownBy(() -> inventoryService.decreaseStockBatch(1L,
                    List.of(new StockItem(1L, 1), new StockItem(2L, 11))))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Available: 10, required: 11");
            then(branchInventoryRepository).should(never()).saveAll(anyList());
            then(stockEventPublisher).should(never()).stockLow(any(), anyString());
        }

        @Test
        @DisplayName("increaseStockBatch - should add stock and update the restock date")
        void increase_ShouldRestore() {
            LocalDateTime before = inventory.getLastRestockDate();
            given(branchInventoryRepository.findByBranchIdAndProductIdIn(1L, Set.of(1L))).willReturn(List.of(inventory));

            inventoryService.increaseStockBatch(1L, List.of(new StockItem(1L, 7)));

            assertThat(inventory.getStock()).isEqualTo(57);
            assertThat(inventory.getLastRestockDate()).isAfter(before);
            then(branchInventoryRepository).should().saveAll(List.of(inventory));
        }

        @Test
        @DisplayName("decreaseStockBatch - should propagate optimistic locking failures")
        void decrease_WhenVersionConflict_ShouldPropagate() {
            given(branchInventoryRepository.findByBranchIdAndProductIdIn(1L, Set.of(1L))).willReturn(List.of(inventory));
            given(branchInventoryRepository.saveAll(anyList()))
                    .willThrow(new ObjectOptimisticLockingFailureException(BranchInventory.class, 101L));

            assertThatThrownBy(() -> inventoryService.decreaseStockBatch(1L, List.of(new StockItem(1L, 1))))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }
    }

    @Nested
    @DisplayName("Stock mutations")
    class StockMutations {
        @Test
        @DisplayName("updateStock - should set stock and minStock, and update lastRestockDate when stock increases")
        void updateStock_WhenIncreasing_ShouldUpdateRestockDate() {
            LocalDateTime before = inventory.getLastRestockDate();
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));
            given(branchInventoryRepository.save(inventory)).willReturn(inventory);

            BranchInventoryResponse result = inventoryService.updateStock(1L, 1L,
                    StockUpdateRequest.builder().stock(80).minStock(10).build());

            assertThat(result.getStock()).isEqualTo(80);
            assertThat(result.getMinStock()).isEqualTo(10);
            assertThat(inventory.getLastRestockDate()).isAfter(before);
        }

        @Test
        @DisplayName("updateStock - should not update lastRestockDate when stock decreases")
        void updateStock_WhenDecreasing_ShouldKeepRestockDate() {
            LocalDateTime before = inventory.getLastRestockDate();
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));
            given(branchInventoryRepository.save(inventory)).willReturn(inventory);

            inventoryService.updateStock(1L, 1L, StockUpdateRequest.builder().stock(20).minStock(5).build());

            assertThat(inventory.getLastRestockDate()).isEqualTo(before);
        }

        @Test
        @DisplayName("updateStock - should create the row after validating the product in catalog when it does not exist yet")
        void updateStock_WhenRowMissing_ShouldCreateIt() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.empty());
            given(branchRepository.findById(1L)).willReturn(Optional.of(branch));
            given(catalogLookupService.requireProduct(1L)).willReturn(product);
            given(branchInventoryRepository.save(any(BranchInventory.class))).willAnswer(inv -> inv.getArgument(0));

            BranchInventoryResponse result = inventoryService.updateStock(1L, 1L,
                    StockUpdateRequest.builder().stock(12).minStock(3).build());

            assertThat(result.getStock()).isEqualTo(12);
            assertThat(result.getProductId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("updateStock - should throw when the product does not exist in catalog")
        void updateStock_WhenProductUnknown_ShouldThrow() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 99L)).willReturn(Optional.empty());
            given(branchRepository.findById(1L)).willReturn(Optional.of(branch));
            given(catalogLookupService.requireProduct(99L))
                    .willThrow(new ResourceNotFoundException("Product not found with ID: 99"));

            assertThatThrownBy(() -> inventoryService.updateStock(1L, 99L,
                    StockUpdateRequest.builder().stock(1).minStock(1).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            then(branchInventoryRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("updateStock - should fail with 503 semantics when catalog is down and the row must be created")
        void updateStock_WhenCatalogDown_ShouldFail() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 5L)).willReturn(Optional.empty());
            given(branchRepository.findById(1L)).willReturn(Optional.of(branch));
            given(catalogLookupService.requireProduct(5L))
                    .willThrow(new RemoteServiceException("catalog-service", "catalog-service is temporarily unavailable"));

            assertThatThrownBy(() -> inventoryService.updateStock(1L, 5L,
                    StockUpdateRequest.builder().stock(1).minStock(1).build()))
                    .isInstanceOf(RemoteServiceException.class);
        }

        @Test
        @DisplayName("adjustStock - should increase stock and set lastRestockDate when delta positive")
        void adjustStock_Positive_ShouldIncrease() {
            LocalDateTime before = inventory.getLastRestockDate();
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));
            given(branchInventoryRepository.save(inventory)).willReturn(inventory);

            BranchInventoryResponse result = inventoryService.adjustStock(1L, 1L,
                    StockAdjustmentRequest.builder().delta(15).reason("Delivery").build());

            assertThat(result.getStock()).isEqualTo(65);
            assertThat(inventory.getLastRestockDate()).isAfter(before);
        }

        @Test
        @DisplayName("adjustStock - should decrease stock without updating lastRestockDate when delta negative")
        void adjustStock_Negative_ShouldDecrease() {
            LocalDateTime before = inventory.getLastRestockDate();
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));
            given(branchInventoryRepository.save(inventory)).willReturn(inventory);

            inventoryService.adjustStock(1L, 1L, StockAdjustmentRequest.builder().delta(-47).reason("Damage").build());

            assertThat(inventory.getStock()).isEqualTo(3);
            assertThat(inventory.getLastRestockDate()).isEqualTo(before);
            then(stockEventPublisher).should().stockLow(inventory, "Premium Rice");
        }

        @Test
        @DisplayName("adjustStock - should throw InsufficientStockException when result would be negative")
        void adjustStock_WhenNegativeResult_ShouldThrow() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 1L)).willReturn(Optional.of(inventory));

            assertThatThrownBy(() -> inventoryService.adjustStock(1L, 1L,
                    StockAdjustmentRequest.builder().delta(-51).build()))
                    .isInstanceOf(InsufficientStockException.class);
            then(branchInventoryRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("adjustStock - should throw when inventory not found")
        void adjustStock_WhenMissing_ShouldThrow() {
            given(branchInventoryRepository.findByBranchIdAndProductId(1L, 99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.adjustStock(1L, 99L,
                    StockAdjustmentRequest.builder().delta(1).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Auto-provisioning driven by catalog")
    class AutoProvisioning {
        @Test
        @DisplayName("initializeInventoryForNewProduct - should create rows only for branches that do not have one yet")
        @SuppressWarnings("unchecked")
        void initializeForNewProduct_ShouldBeIdempotent() {
            Branch other = Branch.builder().id(2L).name("North").address("North Street 2").build();
            given(branchRepository.findAll()).willReturn(List.of(branch, other));
            given(branchInventoryRepository.existsByBranchIdAndProductId(1L, 9L)).willReturn(true);
            given(branchInventoryRepository.existsByBranchIdAndProductId(2L, 9L)).willReturn(false);

            inventoryService.initializeInventoryForNewProduct(9L);

            ArgumentCaptor<List<BranchInventory>> captor = ArgumentCaptor.forClass(List.class);
            then(branchInventoryRepository).should().saveAll(captor.capture());
            assertThat(captor.getValue()).singleElement().satisfies(row -> {
                assertThat(row.getBranch()).isSameAs(other);
                assertThat(row.getStock()).isZero();
                assertThat(row.getMinStock()).isEqualTo(5);
            });
        }

        @Test
        @DisplayName("initializeInventoryForNewBranch - should create rows for every catalog product with stock 0 and minStock 5")
        @SuppressWarnings("unchecked")
        void initializeForNewBranch_ShouldCreateRows() {
            given(catalogLookupService.allProductIds()).willReturn(List.of(1L, 2L));

            inventoryService.initializeInventoryForNewBranch(branch);

            ArgumentCaptor<List<BranchInventory>> captor = ArgumentCaptor.forClass(List.class);
            then(branchInventoryRepository).should().saveAll(captor.capture());
            assertThat(captor.getValue()).extracting(BranchInventory::getProductId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("initializeInventoryForNewBranch - should not fail the branch creation when catalog is down")
        void initializeForNewBranch_WhenCatalogDown_ShouldSkip() {
            given(catalogLookupService.allProductIds())
                    .willThrow(new RemoteServiceException("catalog-service", "down"));

            inventoryService.initializeInventoryForNewBranch(branch);

            then(branchInventoryRepository).should(never()).saveAll(anyList());
        }

        @Test
        @DisplayName("removeProduct - should delete every inventory row of the deleted product")
        void removeProduct_ShouldDeleteRows() {
            inventoryService.removeProduct(4L);
            then(branchInventoryRepository).should().deleteByProductId(eq(4L));
        }
    }
}
