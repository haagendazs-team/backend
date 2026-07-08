package com.haagendazs.payment.payment.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BillingKeyIssueRequest(
        @NotNull
        String orderNo,
        @NotBlank
        String authKey,
        @NotBlank
        String customerKey
) {
}
