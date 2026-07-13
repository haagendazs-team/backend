package com.haagendazs.payment.global.kafka.dto;

import java.time.LocalDateTime;

public record NotificationPushEvent(
        String eventTypeCode,
        String memberId,
        NotificationDispatchType DispatchType,
        LocalDateTime scheduledAt,
        Object payload
) {
    public static NotificationPushEvent immediate(
            String eventTypeCode,
            Long memberId,
            Object payload
    ) {
        return new NotificationPushEvent(
                eventTypeCode,
                String.valueOf(memberId),
                NotificationDispatchType.IMMEDIATE,
                null,
                payload
        );
    }
}
