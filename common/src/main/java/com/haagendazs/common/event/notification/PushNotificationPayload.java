package com.haagendazs.common.event.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

public record PushNotificationPayload(
        @JsonProperty("memberId") String memberId,
        @JsonProperty("DispatchType") DispatchType dispatchType,
        @JsonProperty("scheduledAt") LocalDateTime scheduledAt,
        @JsonProperty("payload") Map<String, Object> payload
) {
}
