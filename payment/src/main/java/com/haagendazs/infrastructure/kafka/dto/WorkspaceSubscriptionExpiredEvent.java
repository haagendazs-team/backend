package com.haagendazs.infrastructure.kafka.dto;

import java.time.LocalDateTime;

public record WorkspaceSubscriptionExpiredEvent(
        Long workspace_id,
        String subscription,
        LocalDateTime expired_at
) {
}
