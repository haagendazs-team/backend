package com.haagendazs.payment.global.kafka.dto;

import java.time.LocalDateTime;

public record SubscriptionChangedEvent(
        Long workspaceId,
        int searchableDays,
        LocalDateTime changedAt
) {
}
