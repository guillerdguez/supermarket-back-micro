package com.supermarket.catalogservice.event;

import com.supermarket.catalogservice.model.product.Product;
import com.supermarket.commons.kafka.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public void productCreated(Product product) {
        domainEventPublisher.publishAfterCommit(ProductTopics.CREATED, toEvent(ProductTopics.CREATED, product));
    }

    public void productDeleted(Product product) {
        domainEventPublisher.publishAfterCommit(ProductTopics.DELETED, toEvent(ProductTopics.DELETED, product));
    }

    private ProductEvent toEvent(String type, Product product) {
        return new ProductEvent(UUID.randomUUID().toString(), type, LocalDateTime.now(),
                product.getId(), product.getName(), product.getCategory(), product.getPrice());
    }
}
