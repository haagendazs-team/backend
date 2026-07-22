package com.haagendazs.infrastructure.kafka.dto;

import java.time.LocalDateTime;

public record WorkspaceSubscribedEvent(
        Long workspace_id,
        String subscription,
        LocalDateTime activated_at
) {
}
