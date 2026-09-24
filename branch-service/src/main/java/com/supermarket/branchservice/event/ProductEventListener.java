package com.supermarket.branchservice.event;

import com.supermarket.branchservice.service.business.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {

    private final InventoryService inventoryService;

    @KafkaListener(topics = BranchTopics.PRODUCT_CREATED)
    public void onProductCreated(ProductEvent event) {
        log.info("Received {} for product {}", event.eventType(), event.productId());
        inventoryService.initializeInventoryForNewProduct(event.productId());
    }

    @KafkaListener(topics = BranchTopics.PRODUCT_DELETED)
    public void onProductDeleted(ProductEvent event) {
        log.info("Received {} for product {}", event.eventType(), event.productId());
        inventoryService.removeProduct(event.productId());
    }
}
