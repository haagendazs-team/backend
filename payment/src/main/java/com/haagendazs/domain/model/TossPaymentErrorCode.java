package com.haagendazs.domain.model;

import java.util.Arrays;

public enum TossPaymentErrorCode {
    // 카드 자동결제 빌링키 발급 요청
    NOT_FOUND_BILLING(
            "NOT_FOUND_BILLING",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "존재하지 않는 빌링 결제 인증 정보 입니다."
    ),
    INVALID_CARD_NUMBER(
            "INVALID_CARD_NUMBER",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "카드번호를 다시 확인해야 합니다."
    ),
    NOT_SUPPORTED_CARD_TYPE(
            "NOT_SUPPORTED_CARD_TYPE",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "지원되지 않는 카드 종류입니다."
    ),
    INVALID_CARD_PASSWORD(
            "INVALID_CARD_PASSWORD",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "카드 비밀번호 정보를 다시 확인해야 합니다."
    ),
    INVALID_CARD_EXPIRATION(
            "INVALID_CARD_EXPIRATION",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "카드 유효기간 정보를 다시 확인해야 합니다."
    ),
    INVALID_CARD_IDENTITY(
            "INVALID_CARD_IDENTITY",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "주민번호 또는 사업자번호가 카드 소유주 정보와 일치하지 않습니다."
    ),
    INVALID_REJECT_CARD(
            "INVALID_REJECT_CARD",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "카드 사용이 거절되었습니다."
    ),
    INVALID_STOPPED_CARD(
            "INVALID_STOPPED_CARD",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "정지된 카드입니다."
    ),
    INVALID_BIRTH_DAY_FORMAT(
            "INVALID_BIRTH_DAY_FORMAT",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "생년월일 또는 사업자등록번호 형식이 올바르지 않습니다."
    ),
    NOT_REGISTERED_CARD_COMPANY(
            "NOT_REGISTERED_CARD_COMPANY",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "카드사에 카드 사용 등록이 필요합니다."
    ),
    INVALID_EMAIL(
            "INVALID_EMAIL",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "이메일 주소 형식이 올바르지 않습니다."
    ),
    NOT_SUPPORTED_METHOD(
            "NOT_SUPPORTED_METHOD",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "지원되지 않는 결제수단입니다."
    ),
    INVALID_REQUEST(
            "INVALID_REQUEST",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "잘못된 요청입니다."
    ),
    EXCEED_MAX_AUTH_COUNT(
            "EXCEED_MAX_AUTH_COUNT",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "최대 인증 횟수를 초과했습니다."
    ),
    REJECT_CARD_COMPANY(
            "REJECT_CARD_COMPANY",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "카드사에서 결제 승인을 거절했습니다."
    ),
    REJECT_ACCOUNT_PAYMENT(
            "REJECT_ACCOUNT_PAYMENT",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "잔액 부족으로 결제에 실패했습니다."
    ),
    COMMON_ERROR(
            "COMMON_ERROR",
            TossPaymentErrorAction.RETRY_LATER,
            "일시적인 오류입니다."
    ),

    // 카드 자동결제 승인
    INVALID_BILL_KEY_REQUEST(
            "INVALID_BILL_KEY_REQUEST",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "빌링키 인증이 완료되지 않았거나 유효하지 않은 빌링 거래입니다."
    ),
    DUPLICATED_ORDER_ID(
            "DUPLICATED_ORDER_ID",
            TossPaymentErrorAction.QUERY_AND_RECONCILE,
            "이미 승인 또는 취소가 진행된 주문번호입니다."
    ),
    NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT(
            "NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "할부가 지원되지 않는 카드 또는 가맹점입니다."
    ),
    INVALID_CARD_INSTALLMENT_PLAN(
            "INVALID_CARD_INSTALLMENT_PLAN",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "할부 개월 정보가 잘못되었습니다."
    ),
    NOT_MATCHES_CUSTOMER_KEY(
            "NOT_MATCHES_CUSTOMER_KEY",
            TossPaymentErrorAction.REQUIRE_BILLING_METHOD_REISSUE,
            "빌링 인증 customerKey와 결제 요청 customerKey가 일치하지 않습니다."
    ),
    BELOW_MINIMUM_AMOUNT(
            "BELOW_MINIMUM_AMOUNT",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "최소 결제 금액보다 작은 금액입니다."
    ),
    NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN(
            "NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "할부가 지원되지 않는 카드입니다."
    ),
    UNAUTHORIZED_KEY(
            "UNAUTHORIZED_KEY",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "인증되지 않은 시크릿 키 또는 클라이언트 키입니다."
    ),
    REJECT_CARD_PAYMENT(
            "REJECT_CARD_PAYMENT",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "한도 초과 또는 잔액 부족으로 결제에 실패했습니다."
    ),
    INCORRECT_BASIC_AUTH_FORMAT(
            "INCORRECT_BASIC_AUTH_FORMAT",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "Authorization 헤더 형식이 올바르지 않습니다."
    ),
    FAILED_INTERNAL_SYSTEM_PROCESSING(
            "FAILED_INTERNAL_SYSTEM_PROCESSING",
            TossPaymentErrorAction.RETRY_LATER,
            "토스 내부 시스템 처리 작업이 실패했습니다."
    ),
    FAILED_DB_PROCESSING(
            "FAILED_DB_PROCESSING",
            TossPaymentErrorAction.RETRY_LATER,
            "토스 DB 처리 중 오류가 발생했습니다."
    ),
    FAILED_CARD_COMPANY_RESPONSE(
            "FAILED_CARD_COMPANY_RESPONSE",
            TossPaymentErrorAction.RETRY_LATER,
            "카드사 응답 오류입니다."
    ),
    TOSS_CONNECT_TIMEOUT(
            "TOSS_CONNECT_TIMEOUT",
            TossPaymentErrorAction.RETRY_LATER,
            "토스페이먼츠 연결 시간이 초과되었습니다."
    ),
    TOSS_READ_TIMEOUT(
            "TOSS_READ_TIMEOUT",
            TossPaymentErrorAction.QUERY_AND_RECONCILE,
            "토스페이먼츠 응답 대기 시간이 초과되었습니다."
    ),

    UNKNOWN(
            "UNKNOWN_TOSS_ERROR",
            TossPaymentErrorAction.FAIL_AND_NOTIFY,
            "분류되지 않은 토스 자동결제 오류입니다."
    );

    private final String code;
    private final TossPaymentErrorAction action;
    private final String description;

    TossPaymentErrorCode(
            String code,
            TossPaymentErrorAction action,
            String description
    ) {
        this.code = code;
        this.action = action;
        this.description = description;
    }

    public static TossPaymentErrorCode from(String code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.code.equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public String getCode() {
        return code;
    }

    public TossPaymentErrorAction getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }
}
