package com.haagendazs.payment.subscription.service.dto;

import com.haagendazs.payment.subscription.enums.PlanType;

import java.time.LocalDateTime;

public record GetSubscriptionResponse(
        Long subscriptionId,
        Long planId,
        String planName,
        PlanType planType,
        LocalDateTime periodEnd
) {
}
