package com.haagendazs.payment.order.enums;

public enum OrderStatus {
    PENDING, //결제대기
    PROCESSING, //결제처리중
    RETRY_SCHEDULED, //자동결제 재시도 예약
    PAID, //결제
    CANCELED, //취소
    EXPIRED, //결제제한시간만료
    FAILED, //실패
    RECONCILE_REQUIRED //PG 결제 성공 후 로컬 후처리 확인 필요
}
