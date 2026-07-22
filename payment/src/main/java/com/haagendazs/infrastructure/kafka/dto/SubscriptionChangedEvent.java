package com.haagendazs.infrastructure.kafka.dto;

import java.time.LocalDateTime;

public record SubscriptionChangedEvent(
        Long workspaceId,
        int searchableDays,
        LocalDateTime changedAt
) {
}
