package com.haagendazs.application.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingMethodIssueRequest(
        @NotBlank
        String authKey
) {
}
