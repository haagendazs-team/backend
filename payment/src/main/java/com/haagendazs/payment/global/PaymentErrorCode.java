package com.haagendazs.payment.global;

import com.haagendazs.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 상품입니다."),
    PRODUCT_SUSPENDED(HttpStatus.BAD_REQUEST, "P002", "판매 중지된 상품입니다."),
    PRODUCT_DETAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "존재하지 않는 상품 상세입니다."),
    UNSUPPORTED_PRODUCT_TYPE(HttpStatus.BAD_REQUEST, "P004", "지원하지 않는 상품 타입입니다."),
    CUSTOMER_KEY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
    "P005",
            "결제 사용자 식별키를 찾을 수 없습니다."
    ),
    CUSTOMER_KEY_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
    "P006",
            "결제 사용자 식별키 생성에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
