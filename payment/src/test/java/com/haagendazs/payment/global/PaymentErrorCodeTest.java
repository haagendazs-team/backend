package com.haagendazs.payment.global;

import com.haagendazs.domain.exception.PaymentErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentErrorCodeTest {

    @Test
    void hasUniqueErrorCodes() {
        assertThat(Arrays.stream(PaymentErrorCode.values())
                .map(PaymentErrorCode::getCode))
                .doesNotHaveDuplicates();
    }

    @Test
    void hasSequentialErrorCodesInDeclarationOrder() {
        assertThat(Arrays.stream(PaymentErrorCode.values())
                .map(PaymentErrorCode::getCode))
                .containsExactly(IntStream.rangeClosed(1, PaymentErrorCode.values().length)
                        .mapToObj(number -> "P%03d".formatted(number))
                        .toArray(String[]::new));
    }
}
