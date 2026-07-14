package com.haagendazs.payment.payment.service.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingMethodIssueAndPayRequest(
        @NotBlank
        String authKey,
        @NotBlank
        String orderNo
) {
}
