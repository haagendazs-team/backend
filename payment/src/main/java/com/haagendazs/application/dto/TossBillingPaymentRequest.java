package com.haagendazs.application.dto;

public record TossBillingPaymentRequest(
        String customerKey,
        String orderId,
        Long amount,
        String orderName
) {
}
