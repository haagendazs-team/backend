package com.haagendazs.infrastructure.kafka.dto;

import java.time.LocalDateTime;

public record PaymentNotificationPayload(
        String paymentStatus,
        String orderNo,
        Long orderId,
        Long workspaceId,
        Long totalAmount,
        String itemName,
        String receiptUrl,
        String failCode,
        String failMessage,
        LocalDateTime occurredAt
) {
}
