package com.haagendazs.infrastructure.toss;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.TossErrorChangingPaymentError;
import com.haagendazs.domain.model.TossPaymentErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class TossPaymentException extends BusinessException {

    // Toss 원본 에러 정보입니다. 재시도 정책, 실패 이력, 운영 로그에서 원인 분기에 사용합니다.
    private final HttpStatusCode httpStatusCode;
    private final String tossCode;
    private final String tossMessage;
    private final TossPaymentErrorCode tossPaymentErrorCode;

    public TossPaymentException(
            HttpStatusCode httpStatusCode,
            String tossCode,
            String tossMessage
    ) {
        this(httpStatusCode, tossCode, tossMessage, TossPaymentErrorCode.from(tossCode));
    }

    private TossPaymentException(
            HttpStatusCode httpStatusCode,
            String tossCode,
            String tossMessage,
            TossPaymentErrorCode tossPaymentErrorCode
    ) {
        super(TossErrorChangingPaymentError.changeErrorCode(tossPaymentErrorCode));
        this.httpStatusCode = httpStatusCode;
        this.tossCode = tossCode;
        this.tossMessage = tossMessage;
        this.tossPaymentErrorCode = tossPaymentErrorCode;
    }
}
