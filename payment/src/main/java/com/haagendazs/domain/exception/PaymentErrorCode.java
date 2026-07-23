package com.haagendazs.domain.exception;

import com.haagendazs.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    // 상품
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 상품입니다."),
    PRODUCT_SUSPENDED(HttpStatus.BAD_REQUEST, "P002", "판매 중지된 상품입니다."),
    PRODUCT_DETAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "존재하지 않는 상품 상세입니다."),
    UNSUPPORTED_PRODUCT_TYPE(HttpStatus.BAD_REQUEST, "P004", "지원하지 않는 상품 타입입니다."),

    // 고객 결제 식별키
    CUSTOMER_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "P005",
            "결제 사용자 식별키를 찾을 수 없습니다."),
    CUSTOMER_KEY_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P006",
            "결제 사용자 식별키 생성에 실패했습니다."),
    INVALID_CUSTOMER_KEY(HttpStatus.NOT_FOUND, "P007", "사용자 식별 키를 찾을 수 없습니다."),

    // 주문
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "P008", "존재하지 않는 주문입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "P009", "해당 주문에 접근할 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "P010", "주문 상태가 올바르지 않습니다."),
    INVALID_ORDER_TYPE(HttpStatus.BAD_REQUEST, "P011", "주문 타입이 올바르지 않습니다."),
    ORDER_EXPIRED(HttpStatus.BAD_REQUEST, "P012", "주문 유효 시간이 만료되었습니다."),

    // 결제 처리
    BILLING_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "P013", "자동결제에 실패했습니다."),
    UNSUPPORTED_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "P014", "지원하지 않는 결제 상태입니다."),
    PAYMENT_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "P015",
            "결제가 거절되었습니다. 잔액 또는 한도를 확인해주세요."),
    INVALID_PAYMENT_REQUEST(HttpStatus.BAD_REQUEST, "P016", "결제 요청정보가 올바르지 않습니다."),
    PAYMENT_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "P017",
            "결제 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

    // 결제수단
    BILLING_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "P018",
            "등록된 결제수단을 찾을 수 없습니다."),
    BILLING_METHOD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "P019",
            "결제수단은 최대 5개까지 등록할 수 있습니다."),
    BILLING_KEY_DELETE_FAILED(HttpStatus.BAD_GATEWAY, "P020",
            "토스페이먼츠 빌링키 삭제에 실패했습니다."),
    PAYMENT_AUTH_FAILED(HttpStatus.BAD_REQUEST, "P021", "결제수단 인증정보가 잘못되었습니다."),
    INVALID_CARD_INFO(HttpStatus.BAD_REQUEST, "P022", "카드정보가 잘못되었습니다."),
    PAYMENT_METHOD_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "P023",
            "현재 결제수단을 사용할 수 없습니다. 다른 결제수단을 등록해주세요."),

    // 구독 및 플랜
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "P024", "플랜을 찾을 수 없습니다."),
    ALREADY_PREMIUM_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "P025",
            "이미 유료 구독 플랜을 이용 중인 워크스페이스입니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "P026", "구독을 찾을 수 없습니다."),
    INVALID_SUBSCRIPTION_CHANGE(HttpStatus.BAD_REQUEST, "P027",
            "변경할 수 없는 구독 플랜입니다."),
    SUBSCRIPTION_SCHEDULED_CHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "P028",
            "예약된 구독 변경을 찾을 수 없습니다."),
    SUBSCRIPTION_SCHEDULED_CHANGE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "P029",
            "이미 예약된 구독 변경이 있습니다."),

    // 메시징 인프라
    KAFKA_MESSAGE_SERIALIZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P030",
            "Kafka 메시지 직렬화에 실패했습니다."),
    KAFKA_MESSAGE_DESERIALIZE_FAILED(HttpStatus.BAD_REQUEST, "P031",
            "Kafka 메시지 역직렬화에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
