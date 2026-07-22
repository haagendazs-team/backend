package com.haagendazs.domain.exception;

import com.haagendazs.domain.model.TossPaymentErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TossErrorChangingPaymentErrorTest {

    @Test
    void mapsEveryTossErrorCodeToAnInternalErrorCode() {
        assertThat(TossPaymentErrorCode.values())
                .allSatisfy(code -> assertThat(TossErrorChangingPaymentError.changeErrorCode(code))
                        .isNotNull());
    }

    @Test
    void compressesDetailedTossErrorsByRequiredUserAction() {
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.INVALID_CARD_PASSWORD))
                .isEqualTo(PaymentErrorCode.INVALID_CARD_INFO);
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.INVALID_BILL_KEY_REQUEST))
                .isEqualTo(PaymentErrorCode.PAYMENT_AUTH_FAILED);
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.INVALID_STOPPED_CARD))
                .isEqualTo(PaymentErrorCode.PAYMENT_METHOD_UNAVAILABLE);
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.REJECT_CARD_PAYMENT))
                .isEqualTo(PaymentErrorCode.PAYMENT_REJECTED);
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.BELOW_MINIMUM_AMOUNT))
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.FAILED_DB_PROCESSING))
                .isEqualTo(PaymentErrorCode.PAYMENT_TEMPORARILY_UNAVAILABLE);
        assertThat(TossErrorChangingPaymentError.changeErrorCode(TossPaymentErrorCode.UNKNOWN))
                .isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED);
    }
}
