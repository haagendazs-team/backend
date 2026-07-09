package com.haagendazs.payment.global.kafka.dto;

import java.time.LocalDateTime;

public record SubscriptionInitializedEvent(
        Long workspaceId,
        int searchableDays,
        LocalDateTime initializedAt
) {
}
