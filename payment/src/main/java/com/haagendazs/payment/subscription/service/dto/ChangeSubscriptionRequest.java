package com.haagendazs.payment.subscription.service.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeSubscriptionRequest(
        @NotNull
        Long productId
) {
}
