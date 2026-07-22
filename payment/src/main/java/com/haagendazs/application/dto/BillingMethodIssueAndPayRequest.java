package com.haagendazs.application.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingMethodIssueAndPayRequest(
        @NotBlank
        String authKey,
        @NotBlank
        String orderNo
) {
}
