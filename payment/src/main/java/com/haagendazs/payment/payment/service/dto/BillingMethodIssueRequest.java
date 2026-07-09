package com.haagendazs.payment.payment.service.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingMethodIssueRequest(
        @NotBlank
        String authKey,
        @NotBlank
        String customerKey
) {
}
