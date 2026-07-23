package com.haagendazs.payment.payment.service.tools;

import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.domain.model.TossPaymentErrorCode;
import com.haagendazs.infrastructure.toss.TossPaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentExceptionTest {

    @Test
    void convertsTossErrorToInternalErrorForApiResponse() {
        TossPaymentException exception = new TossPaymentException(
                HttpStatus.BAD_REQUEST,
                "REJECT_CARD_PAYMENT",
                "Toss original error message"
        );

        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_REJECTED);
        assertThat(exception.getMessage()).isEqualTo(PaymentErrorCode.PAYMENT_REJECTED.getMessage());
    }

    @Test
    void preservesOriginalTossErrorForPolicyAndOperations() {
        TossPaymentException exception = new TossPaymentException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CARD_PASSWORD",
                "Toss original error message"
        );

        assertThat(exception.getTossPaymentErrorCode())
                .isEqualTo(TossPaymentErrorCode.INVALID_CARD_PASSWORD);
        assertThat(exception.getTossCode()).isEqualTo("INVALID_CARD_PASSWORD");
        assertThat(exception.getTossMessage()).isEqualTo("Toss original error message");
    }

    @Test
    void convertsUnknownTossErrorToGenericPaymentFailure() {
        TossPaymentException exception = new TossPaymentException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "NEW_TOSS_ERROR",
                "Unrecognized Toss error"
        );

        assertThat(exception.getTossPaymentErrorCode()).isEqualTo(TossPaymentErrorCode.UNKNOWN);
        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED);
        assertThat(exception.getMessage())
                .isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED.getMessage());
    }
}
