package com.supermarket.commons.kafka;

import com.supermarket.commons.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void publishAfterCommit(String topic, DomainEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(topic, event);
                }
            });
            return;
        }
        publish(topic, event);
    }

    public void publish(String topic, DomainEvent event) {
        try {
            kafkaTemplate.send(topic, event.key(), event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish {} [{}] to {}", event.eventType(), event.eventId(), topic, error);
                        } else {
                            log.debug("Published {} [{}] to {}-{} offset {}", event.eventType(), event.eventId(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException e) {
            log.error("Failed to publish {} [{}] to {}", event.eventType(), event.eventId(), topic, e);
        }
    }
}
