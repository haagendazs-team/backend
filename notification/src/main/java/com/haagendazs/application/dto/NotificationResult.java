package com.haagendazs.application.dto;

import com.haagendazs.domain.model.Notification;
import com.haagendazs.domain.model.Event;

import java.time.LocalDateTime;

public record NotificationResult(
        Long id,
        String eventTypeCode,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResult of(Notification notification, Event event) {
        return new NotificationResult(
                notification.getId(),
                event.getEventTypeCode(),
                event.getPayload(),
                notification.isAlreadyRead(),
                notification.getCreatedAt()
        );
    }
}
