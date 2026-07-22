package com.haagendazs.application.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingPaymentRequest(
        @NotBlank
        String orderNo
) {
}
