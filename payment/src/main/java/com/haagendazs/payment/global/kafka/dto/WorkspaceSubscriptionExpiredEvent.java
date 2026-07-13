package com.haagendazs.payment.global.kafka.dto;

import java.time.LocalDateTime;

public record WorkspaceSubscriptionExpiredEvent(
        Long workspace_id,
        String subscription,
        LocalDateTime expired_at
) {
}
