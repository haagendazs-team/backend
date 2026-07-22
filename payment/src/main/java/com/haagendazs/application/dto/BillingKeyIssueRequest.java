package com.haagendazs.application.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingKeyIssueRequest(
        @NotBlank
        String orderNo,
        @NotBlank
        String authKey,
        @NotBlank
        String customerKey
) {
}
