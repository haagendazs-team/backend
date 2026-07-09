package com.haagendazs.payment.payment.service.dto;

public record TossBillingPaymentRequest(
        String customerKey,
        String orderId,
        Long amount,
        String orderName
) {
}
