package com.haagendazs.payment.Subscription.service.dto;

import com.haagendazs.payment.Subscription.enums.PlanStatus;
import com.haagendazs.payment.Subscription.enums.PlanType;
import com.haagendazs.payment.product.service.dto.ProductDetailResponse;

public record SubscriptionPlanResponse(
        Long subscriptionPlanId,
        String planName,
        PlanType type,
        int durationDays,
        PlanStatus status
) implements ProductDetailResponse {
}
