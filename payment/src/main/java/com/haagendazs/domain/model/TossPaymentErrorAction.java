package com.haagendazs.domain.model;

public enum TossPaymentErrorAction {
    FAIL_AND_NOTIFY, // 결제 실패 처리 후 사용자에게 즉시 알립니다.
    REQUIRE_BILLING_METHOD_REISSUE, // 결제수단 재등록이 필요한 실패입니다.
    QUERY_AND_RECONCILE, // PG 처리 여부가 애매하므로 결제 조회/보정 대상으로 남깁니다.
    RETRY_LATER // 일시 장애 가능성이 있어 재시도 정책 대상으로 분류합니다.
}
