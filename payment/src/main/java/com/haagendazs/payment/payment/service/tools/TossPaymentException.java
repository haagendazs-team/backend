package com.haagendazs.payment.payment.service.tools;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.enums.TossPaymentErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class TossPaymentException extends BusinessException {

    // Toss 원본 에러 정보입니다. 사용자 응답, 실패 알림, 운영 로그에서 원인 분기에 사용합니다.
    private final HttpStatusCode httpStatusCode;
    private final String tossCode;
    private final String tossMessage;
    private final TossPaymentErrorCode tossPaymentErrorCode;

    public TossPaymentException(
            HttpStatusCode httpStatusCode,
            String tossCode,
            String tossMessage
    ) {
        super(
                PaymentErrorCode.BILLING_PAYMENT_FAILED,
                tossMessage == null || tossMessage.isBlank()
                        ? PaymentErrorCode.BILLING_PAYMENT_FAILED.getMessage()
                        : tossMessage
        );
        this.httpStatusCode = httpStatusCode;
        this.tossCode = tossCode;
        this.tossMessage = tossMessage;
        this.tossPaymentErrorCode = TossPaymentErrorCode.from(tossCode);
    }
}
