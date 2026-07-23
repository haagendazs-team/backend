package com.haagendazs.application.dto;

public record TossBillingKeyIssueRequest(
        String authKey,
        String customerKey
) {
}
