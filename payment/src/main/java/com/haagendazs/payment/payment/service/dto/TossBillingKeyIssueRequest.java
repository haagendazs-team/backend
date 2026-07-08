package com.haagendazs.payment.payment.service.dto;

public record TossBillingKeyIssueRequest(
        String authKey,
        String customerKey
) {
}
