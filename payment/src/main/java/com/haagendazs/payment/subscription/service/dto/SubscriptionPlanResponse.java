package com.haagendazs.payment.subscription.service.dto;

import com.haagendazs.payment.subscription.enums.PlanStatus;
import com.haagendazs.payment.subscription.enums.PlanType;
import com.haagendazs.payment.product.service.dto.ProductDetailResponse;

public record SubscriptionPlanResponse(
        Long subscriptionPlanId,
        String planName,
        PlanType type,
        int durationDays,
        PlanStatus status
) implements ProductDetailResponse {
}
