package com.haagendazs.application.dto;

import com.haagendazs.domain.model.Payments;
import com.haagendazs.domain.model.CardCompany;
import com.haagendazs.domain.model.EasyPayProvider;
import com.haagendazs.domain.model.PaymentMethod;
import com.haagendazs.domain.model.PaymentProvider;
import com.haagendazs.domain.model.PaymentStatus;
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
