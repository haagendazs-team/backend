package com.haagendazs.application.listener;

import java.time.LocalDateTime;

public record NotificationPermanentlyFailedEvent(
        Long eventId,
        String eventTypeCode,
        int retryCount,
        String source,
        String payload,
        LocalDateTime occurredAt
) {
    public static NotificationPermanentlyFailedEvent of(Long eventId, String eventTypeCode,
                                                         int retryCount, String source, String payload) {
        return new NotificationPermanentlyFailedEvent(
                eventId, eventTypeCode, retryCount, source, payload, LocalDateTime.now());
    }
}
