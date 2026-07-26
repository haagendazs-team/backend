package com.haagendazs.application.dto;

import java.time.LocalDateTime;

public record ScheduledMessageCreateRequest(
        String content,
        LocalDateTime scheduledAt
) {}
