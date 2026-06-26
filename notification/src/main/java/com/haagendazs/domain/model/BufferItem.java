package com.haagendazs.domain.model;

import org.springframework.data.redis.connection.stream.RecordId;

public record BufferItem(
        Notification notification,
        String subject,
        String payload,
        String streamKey,
        RecordId recordId
) {
    public static BufferItem of(Notification notification, String subject, String payload,
                                             String streamKey, RecordId recordId) {
        return new BufferItem(notification, subject, payload, streamKey, recordId);
    }
}
