package com.haagendazs.application.dto;

import com.haagendazs.domain.model.PlanStatus;
import com.haagendazs.domain.model.PlanType;
import com.haagendazs.application.dto.ProductDetailResponse;

public record SubscriptionPlanResponse(
        Long subscriptionPlanId,
        String planName,
        PlanType type,
        int durationDays,
        PlanStatus status
) implements ProductDetailResponse {
}
