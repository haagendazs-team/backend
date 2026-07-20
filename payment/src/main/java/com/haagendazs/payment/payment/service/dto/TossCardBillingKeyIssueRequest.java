package com.haagendazs.payment.payment.service.dto;

public record TossCardBillingKeyIssueRequest(
        String customerKey,
        String cardNumber,
        String cardExpirationYear,
        String cardExpirationMonth,
        String customerIdentityNumber,
        String cardPassword,
        String customerName,
        String customerEmail
) {
}
