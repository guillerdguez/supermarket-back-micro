package com.supermarket.branchservice.service.business.impl;

import com.supermarket.branchservice.client.CatalogLookupService;
import com.supermarket.branchservice.client.ProductSummary;
import com.supermarket.branchservice.config.CacheConfig;
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
import com.supermarket.branchservice.service.business.InventoryService;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final int DEFAULT_MIN_STOCK = 5;

    private final BranchInventoryRepository branchInventoryRepository;
    private final BranchRepository branchRepository;
    private final BranchInventoryMapper branchInventoryMapper;
    private final CatalogLookupService catalogLookupService;
    private final StockEventPublisher stockEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Integer getStockInBranch(Long branchId, Long productId) {
        return branchInventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .map(BranchInventory::getStock)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getMinStockInBranch(Long branchId, Long productId) {
        return branchInventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .map(BranchInventory::getMinStock)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.LOW_STOCK_CACHE, key = "'branch:' + #branchId")
    public List<LowStockAlertResponse> getLowStockInBranch(Long branchId) {
        return toLowStockAlerts(branchInventoryRepository.findLowStockByBranchId(branchId));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.LOW_STOCK_CACHE, key = "'global'")
    public List<LowStockAlertResponse> getLowStockGlobal() {
        return toLowStockAlerts(branchInventoryRepository.findLowStockGlobal());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchInventoryResponse> getBranchInventory(Long branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Branch not found with id: " + branchId);
        }
        List<BranchInventory> inventory = branchInventoryRepository.findByBranchId(branchId);
        return branchInventoryMapper.toResponseList(inventory, productsOf(inventory));
    }

    @Override
    @Transactional(readOnly = true)
    public TotalStockResponse getTotalStockByProduct(Long productId) {
        ProductSummary product = catalogLookupService.requireProduct(productId);

        long total = branchInventoryRepository.findByProductId(productId).stream()
                .mapToLong(BranchInventory::getStock)
                .sum();

        return TotalStockResponse.builder()
                .productId(product.id())
                .productName(product.name())
                .totalStock(total)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LOW_STOCK_CACHE, allEntries = true)
    public BranchInventoryResponse updateStock(Long branchId, Long productId, StockUpdateRequest request) {
        BranchInventory inventory = branchInventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .orElseGet(() -> newInventoryRow(branchId, productId));

        int previousStock = inventory.getStock();
        boolean isRestock = request.getStock() > previousStock;

        inventory.setStock(request.getStock());
        inventory.setMinStock(request.getMinStock());

        if (isRestock) {
            inventory.setLastRestockDate(LocalDateTime.now());
        }

        BranchInventory saved = branchInventoryRepository.save(inventory);

        log.info("Updated stock for product {} in branch {}. Stock: {} -> {}. Restock: {}",
                productId, branchId, previousStock, saved.getStock(), isRestock);

        Map<Long, ProductSummary> products = productsOf(List.of(saved));
        checkAndNotifyLowStock(saved, products);

        return branchInventoryMapper.toResponse(saved, products.get(productId));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LOW_STOCK_CACHE, allEntries = true)
    public BranchInventoryResponse adjustStock(Long branchId, Long productId, StockAdjustmentRequest request) {
        BranchInventory inventory = findInventory(branchId, productId);

        int newStock = inventory.getStock() + request.getDelta();
        if (newStock < 0) {
            throw new InsufficientStockException(
                    String.format("Adjustment would result in negative stock. Current: %d, delta: %d",
                            inventory.getStock(), request.getDelta()));
        }

        inventory.setStock(newStock);
        if (request.getDelta() > 0) {
            inventory.setLastRestockDate(LocalDateTime.now());
        }

        BranchInventory saved = branchInventoryRepository.save(inventory);

        log.info("Stock adjusted for product {} in branch {} by {} (reason: {}). New stock: {}",
                productId, branchId, request.getDelta(), request.getReason(), newStock);

        Map<Long, ProductSummary> products = productsOf(List.of(saved));
        checkAndNotifyLowStock(saved, products);
        return branchInventoryMapper.toResponse(saved, products.get(productId));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LOW_STOCK_CACHE, allEntries = true)
    public void decreaseStockBatch(Long branchId, List<StockItem> items) {
        log.info("Reducing batch stock for branch {} with {} items", branchId, items.size());

        Map<Long, Integer> requiredQuantities = buildQuantityMap(items);
        List<BranchInventory> inventories = loadInventories(branchId, requiredQuantities.keySet());
        Map<Long, ProductSummary> products = productsOf(inventories);
        verifySufficientStockBatch(inventories, requiredQuantities, products);

        inventories.forEach(inv -> inv.setStock(inv.getStock() - requiredQuantities.get(inv.getProductId())));
        List<BranchInventory> saved = branchInventoryRepository.saveAll(inventories);

        saved.forEach(inventory -> checkAndNotifyLowStock(inventory, products));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LOW_STOCK_CACHE, allEntries = true)
    public void increaseStockBatch(Long branchId, List<StockItem> items) {
        log.info("Restoring batch stock for branch {} with {} items", branchId, items.size());

        Map<Long, Integer> quantitiesToRestore = buildQuantityMap(items);
        List<BranchInventory> inventories = loadInventories(branchId, quantitiesToRestore.keySet());

        inventories.forEach(inv -> {
            inv.setStock(inv.getStock() + quantitiesToRestore.get(inv.getProductId()));
            inv.setLastRestockDate(LocalDateTime.now());
        });

        branchInventoryRepository.saveAll(inventories);
        log.info("Stock restored for {} products in branch {}", inventories.size(), branchId);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LOW_STOCK_CACHE, allEntries = true)
    public void initializeInventoryForNewProduct(Long productId) {
        List<BranchInventory> newInventories = branchRepository.findAll().stream()
                .filter(branch -> !branchInventoryRepository.existsByBranchIdAndProductId(branch.getId(), productId))
                .map(branch -> emptyRow(branch, productId))
                .toList();

        branchInventoryRepository.saveAll(newInventories);
        log.info("Initialized inventory for new product {} across {} branches", productId, newInventories.size());
    }

    @Override
    @Transactional
    public void initializeInventoryForNewBranch(Branch branch) {
        List<Long> productIds;
        try {
            productIds = catalogLookupService.allProductIds();
        } catch (RemoteServiceException e) {
            log.warn("Catalog unavailable while creating branch '{}'. Inventory rows will be created on demand",
                    branch.getName());
            return;
        }

        List<BranchInventory> newInventories = productIds.stream()
                .map(productId -> emptyRow(branch, productId))
                .toList();

        branchInventoryRepository.saveAll(newInventories);
        log.info("Initialized inventory for new branch '{}' across {} products",
                branch.getName(), newInventories.size());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.LOW_STOCK_CACHE, allEntries = true)
    public void removeProduct(Long productId) {
        int removed = branchInventoryRepository.deleteByProductId(productId);
        log.info("Removed {} inventory rows of deleted product {}", removed, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLevel> getStockLevels(Long branchId) {
        return branchInventoryRepository.findAllForBranchOrGlobal(branchId).stream()
                .map(inv -> new StockLevel(inv.getBranch().getId(), inv.getProductId(), inv.getStock(), inv.getMinStock()))
                .toList();
    }

    private BranchInventory newInventoryRow(Long branchId, Long productId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + branchId));
        catalogLookupService.requireProduct(productId);
        return emptyRow(branch, productId);
    }

    private BranchInventory emptyRow(Branch branch, Long productId) {
        return BranchInventory.builder()
                .branch(branch)
                .productId(productId)
                .stock(0)
                .minStock(DEFAULT_MIN_STOCK)
                .lastRestockDate(LocalDateTime.now())
                .build();
    }

    private BranchInventory findInventory(Long branchId, Long productId) {
        return branchInventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Product %d not found in branch %d", productId, branchId)
                ));
    }

    private void checkAndNotifyLowStock(BranchInventory inventory, Map<Long, ProductSummary> products) {
        if (inventory.getStock() <= inventory.getMinStock()) {
            try {
                stockEventPublisher.stockLow(inventory, productName(inventory.getProductId(), products));
            } catch (Exception e) {
                log.warn("Failed to publish low stock event for product {} in branch {}: {}",
                        inventory.getProductId(), inventory.getBranch().getId(), e.getMessage());
            }
        }
    }

    private Map<Long, Integer> buildQuantityMap(List<StockItem> items) {
        validateItems(items);
        return items.stream().collect(Collectors.groupingBy(
                StockItem::productId,
                Collectors.summingInt(StockItem::quantity)
        ));
    }

    private void validateItems(List<StockItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Details list cannot be empty");
        }
        for (int i = 0; i < items.size(); i++) {
            StockItem item = items.get(i);
            if (item.productId() == null) {
                throw new IllegalArgumentException("Detail at position " + i + " does not have a productId");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException(
                        String.format("Invalid quantity (%d) for product %d", item.quantity(), item.productId()));
            }
        }
    }

    private List<BranchInventory> loadInventories(Long branchId, Set<Long> productIds) {
        List<BranchInventory> inventories =
                branchInventoryRepository.findByBranchIdAndProductIdIn(branchId, productIds);

        if (inventories.size() != productIds.size()) {
            Set<Long> foundIds = inventories.stream()
                    .map(BranchInventory::getProductId)
                    .collect(Collectors.toSet());

            List<Long> missing = productIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .sorted()
                    .toList();

            throw new ResourceNotFoundException(
                    String.format("The following products do not exist in branch %d: %s", branchId, missing)
            );
        }
        return inventories;
    }

    private void verifySufficientStockBatch(List<BranchInventory> inventories, Map<Long, Integer> required,
                                            Map<Long, ProductSummary> products) {
        for (BranchInventory inv : inventories) {
            int needed = required.get(inv.getProductId());
            if (inv.getStock() < needed) {
                throw new InsufficientStockException(
                        String.format("Insufficient stock for product '%s' (ID: %d) in branch %d. Available: %d, required: %d",
                                productName(inv.getProductId(), products), inv.getProductId(),
                                inv.getBranch().getId(), inv.getStock(), needed)
                );
            }
        }
    }

    private List<LowStockAlertResponse> toLowStockAlerts(List<BranchInventory> inventories) {
        Map<Long, ProductSummary> products = productsOf(inventories);
        return inventories.stream()
                .map(inventory -> LowStockAlertResponse.builder()
                        .branchId(inventory.getBranch().getId())
                        .branchName(inventory.getBranch().getName())
                        .productId(inventory.getProductId())
                        .productName(productName(inventory.getProductId(), products))
                        .currentStock(inventory.getStock())
                        .minStock(inventory.getMinStock())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Long, ProductSummary> productsOf(List<BranchInventory> inventories) {
        Set<Long> productIds = inventories.stream()
                .map(BranchInventory::getProductId)
                .collect(Collectors.toSet());
        return catalogLookupService.productsById(productIds);
    }

    private String productName(Long productId, Map<Long, ProductSummary> products) {
        ProductSummary product = products.get(productId);
        return product != null ? product.name() : "Product #" + productId;
    }
}
