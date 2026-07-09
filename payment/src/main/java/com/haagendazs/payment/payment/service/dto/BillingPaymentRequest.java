package com.haagendazs.payment.payment.service.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingPaymentRequest(
        @NotBlank
        String orderNo
) {
}
