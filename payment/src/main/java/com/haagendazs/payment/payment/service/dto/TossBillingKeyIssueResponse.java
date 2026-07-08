package com.haagendazs.payment.payment.service.dto;

public record TossBillingKeyIssueResponse(
        String billingKey,
        String customerKey,
        String method,
        CardInfo card
) {
    public record CardInfo(
            String issuerCode,
            String number,
            String cardType,
            String ownerType
    ) {
    }
}
