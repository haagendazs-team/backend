package com.haagendazs.payment.subscription.service.dto;

import com.haagendazs.payment.subscription.enums.PlanType;
import java.time.LocalDateTime;

public record ScheduledPlanChangeResponse(
        boolean exists,
        Long currentPlanId,
        String currentPlanName,
        PlanType currentPlanType,
        Long requestedPlanId,
        String requestedPlanName,
        PlanType requestedPlanType,
        LocalDateTime scheduledAt,
        boolean cancelable
) {
    public static ScheduledPlanChangeResponse none(
            Long currentPlanId,
            String currentPlanName,
            PlanType currentPlanType
    ) {
        return new ScheduledPlanChangeResponse(
                false,
                currentPlanId,
                currentPlanName,
                currentPlanType,
                null,
                null,
                null,
                null,
                false
        );
    }
}
