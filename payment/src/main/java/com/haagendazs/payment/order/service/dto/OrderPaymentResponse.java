package com.haagendazs.payment.order.service.dto;

import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.EasyPayProvider;
import com.haagendazs.payment.payment.enums.PaymentMethod;
import com.haagendazs.payment.payment.enums.PaymentProvider;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import java.time.LocalDateTime;

public record OrderPaymentResponse(
        Long paymentId,
        String paymentKey,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        PaymentStatus paymentStatus,
        Long totalAmount,
        String failCode,
        LocalDateTime approvedAt,
        CardCompany cardCompany,
        String cardNumber,
        EasyPayProvider easyPayProvider,
        String receiptUrl,
        LocalDateTime createdAt
) {
    public static OrderPaymentResponse from(Payments payment) {
        return new OrderPaymentResponse(
                payment.getId(),
                payment.getPaymentKey(),
                payment.getPaymentMethod(),
                payment.getPaymentProvider(),
                payment.getPaymentStatus(),
                payment.getTotalAmount(),
                payment.getFailCode(),
                payment.getApprovedAt(),
                payment.getCardCompany(),
                payment.getCardNumber(),
                payment.getEasyPayProvider(),
                payment.getReceiptUrl(),
                payment.getCreatedAt()
        );
    }
}
