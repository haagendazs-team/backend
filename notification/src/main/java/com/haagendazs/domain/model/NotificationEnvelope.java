package com.haagendazs.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record NotificationEnvelope(
        Long memberId,
        DispatchType isDispatchType,
        LocalDateTime scheduledAt,
        Object payload
) {
    public enum DispatchType { IMMEDIATE, SCHEDULED }

    @JsonCreator
    public static NotificationEnvelope of(
            @JsonProperty("memberId") Long memberId,
            @JsonAlias({"DispatchType", "dispatchType"})
            @JsonProperty("isDispatchType") DispatchType isDispatchType,
            @JsonProperty("scheduledAt") LocalDateTime scheduledAt,
            @JsonProperty("payload") Object payload
    ) {
        return new NotificationEnvelope(memberId, isDispatchType, scheduledAt, payload);
    }

    public boolean isScheduled() {
        return isDispatchType == DispatchType.SCHEDULED && scheduledAt != null;
    }
}
