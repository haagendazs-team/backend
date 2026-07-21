package com.haagendazs.payment.global.kafka;


import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.enums.TossPaymentErrorCode;

public class TossErrorChangingPaymentError {
    public PaymentErrorCode changeErrorCode(TossPaymentErrorCode tossPaymentErrorCode) {
        switch (tossPaymentErrorCode) {
            case NOT_FOUND_BILLING:
                return PaymentErrorCode.PAYMENT_AUTH_FAILED;
            case INVALID_CARD_NUMBER:
                return PaymentErrorCode.INVALID_CARD_INFO;
            case NOT_SUPPORTED_CARD_TYPE:
                return PaymentErrorCode.INVALID_CARD_INFO;
            case INVALID_CARD_PASSWORD:
                return PaymentErrorCode.INVALID_CARD_INFO;
            case INVALID_CARD_EXPIRATION:
                return PaymentErrorCode.INVALID_CARD_INFO;
            case INVALID_CARD_IDENTITY:
                return PaymentErrorCode.INVALID_CARD_INFO;
            case INVALID_REJECT_CARD:
                return PaymentErrorCode.
        }
    }
}
