package com.haagendazs.payment.payment.service.dto;

public record TossBillingPaymentResponse(
        String paymentKey,
        String orderId,
        String orderName,
        String status,
        Long totalAmount,
        String requestedAt,
        String approvedAt,
        TossCardInfo card,
        TossReceipt receipt
) {
    public record TossCardInfo(
            String issuerCode,
            String acquirerCode,
            String number,
            String cardType,
            String ownerType
    ) {
    }

    public record TossReceipt(
            String url
    ) {
    }
}
