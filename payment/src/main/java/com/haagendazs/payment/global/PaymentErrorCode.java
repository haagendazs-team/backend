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
    CUSTOMER_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "P005",
            "결제 사용자 식별키를 찾을 수 없습니다."
    ),
    CUSTOMER_KEY_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P006",
            "결제 사용자 식별키 생성에 실패했습니다."
    ),
    INVALID_CUSTOMER_KEY(HttpStatus.NOT_FOUND, "P007", "사용자 식별 키를 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "P008", "존재하지 않는 주문입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "P009", "해당 주문에 접근할 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "P010", "주문 상태가 올바르지 않습니다."),
    INVALID_ORDER_TYPE(HttpStatus.BAD_REQUEST, "P011", "주문 타입이 올바르지 않습니다."),
    ORDER_EXPIRED(HttpStatus.BAD_REQUEST, "P012", "주문 유효 시간이 만료되었습니다."),
    BILLING_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "P013", "자동결제에 실패했습니다."),
    UNSUPPORTED_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "P013",
            "지원하지 않는 결제 상태입니다."
    ),
    KAFKA_MESSAGE_SERIALIZE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "P015",
            "Kafka 메시지 직렬화에 실패했습니다."
    ),
    KAFKA_MESSAGE_DESERIALIZE_FAILED(
            HttpStatus.BAD_REQUEST,
            "P016",
            "Kafka 메시지 역직렬화에 실패했습니다."
    ),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "P017", "플랜을 찾을 수 없습니다."),
    ALREADY_PREMIUM_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "P017",
            "이미 유료 구독 플랜을 이용 중인 워크스페이스입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
