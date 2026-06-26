package com.haagendazs.presentation.dto;

import com.haagendazs.application.dto.NotificationResult;

import java.time.Instant;
import java.time.ZoneOffset;

public record NotificationResponse(
        Long id,
        String eventTypeCode,
        String payload,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(
                result.id(),
                result.eventTypeCode(),
                result.payload(),
                result.read(),
                result.createdAt().toInstant(ZoneOffset.UTC)
        );
    }
}
