package com.haagendazs.domain.exception;

import com.haagendazs.domain.model.TossPaymentErrorCode;

public final class TossErrorChangingPaymentError {

    private TossErrorChangingPaymentError() {
    }

    public static PaymentErrorCode changeErrorCode(TossPaymentErrorCode tossPaymentErrorCode) {
        return switch (tossPaymentErrorCode) {
            case INVALID_CARD_NUMBER,
                 NOT_SUPPORTED_CARD_TYPE,
                 INVALID_CARD_PASSWORD,
                 INVALID_CARD_EXPIRATION,
                 INVALID_CARD_IDENTITY,
                 INVALID_BIRTH_DAY_FORMAT -> PaymentErrorCode.INVALID_CARD_INFO;

            case NOT_FOUND_BILLING,
                 EXCEED_MAX_AUTH_COUNT,
                 INVALID_BILL_KEY_REQUEST,
                 NOT_MATCHES_CUSTOMER_KEY -> PaymentErrorCode.PAYMENT_AUTH_FAILED;

            case INVALID_REJECT_CARD,
                 INVALID_STOPPED_CARD,
                 NOT_REGISTERED_CARD_COMPANY,
                 REJECT_CARD_COMPANY -> PaymentErrorCode.PAYMENT_METHOD_UNAVAILABLE;

            case REJECT_ACCOUNT_PAYMENT,
                 REJECT_CARD_PAYMENT -> PaymentErrorCode.PAYMENT_REJECTED;

            case INVALID_EMAIL,
                 NOT_SUPPORTED_METHOD,
                 INVALID_REQUEST,
                 NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT,
                 INVALID_CARD_INSTALLMENT_PLAN,
                 BELOW_MINIMUM_AMOUNT,
                 NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN -> PaymentErrorCode.INVALID_PAYMENT_REQUEST;

            case COMMON_ERROR,
                 DUPLICATED_ORDER_ID,
                 FAILED_INTERNAL_SYSTEM_PROCESSING,
                 FAILED_DB_PROCESSING,
                 FAILED_CARD_COMPANY_RESPONSE,
                 TOSS_CONNECT_TIMEOUT,
                 TOSS_READ_TIMEOUT -> PaymentErrorCode.PAYMENT_TEMPORARILY_UNAVAILABLE;

            case UNAUTHORIZED_KEY,
                 INCORRECT_BASIC_AUTH_FORMAT,
                 UNKNOWN -> PaymentErrorCode.BILLING_PAYMENT_FAILED;
        };
    }
}
