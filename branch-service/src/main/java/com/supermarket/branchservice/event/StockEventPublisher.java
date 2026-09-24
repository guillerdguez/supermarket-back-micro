package com.supermarket.branchservice.event;

import com.supermarket.branchservice.model.branch.BranchInventory;
import com.supermarket.commons.kafka.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public void stockLow(BranchInventory inventory, String productName) {
        StockLowEvent event = new StockLowEvent(
                UUID.randomUUID().toString(),
                BranchTopics.STOCK_LOW,
                LocalDateTime.now(),
                inventory.getBranch().getId(),
                inventory.getBranch().getName(),
                inventory.getProductId(),
                productName,
                inventory.getStock(),
                inventory.getMinStock());
        domainEventPublisher.publishAfterCommit(BranchTopics.STOCK_LOW, event);
    }
}
