package com.haagendazs.application.dto;

import com.haagendazs.domain.model.PlanType;

import java.time.LocalDateTime;

public record GetSubscriptionResponse(
        Long subscriptionId,
        Long planId,
        String planName,
        PlanType planType,
        LocalDateTime periodEnd
) {
}
