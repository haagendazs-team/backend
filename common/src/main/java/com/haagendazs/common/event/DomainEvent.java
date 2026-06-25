package com.haagendazs.common.event;

import java.time.Instant;

public interface DomainEvent {
    String getEventId();
    String getEventType();
    Instant getOccurredAt();
}
