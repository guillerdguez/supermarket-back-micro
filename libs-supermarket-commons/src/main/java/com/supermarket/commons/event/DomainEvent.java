package com.supermarket.commons.event;

import java.time.LocalDateTime;

public interface DomainEvent {

    String eventId();

    String eventType();

    LocalDateTime occurredAt();

    String key();
}
