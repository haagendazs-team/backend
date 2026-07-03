package com.haagendazs.payment.payment.enums;

public enum CancelReason {
    USER_REQUEST,        // 사용자 요청
    DUPLICATE_PAYMENT,   // 중복 결제
    WRONG_PRODUCT,       // 잘못된 상품 구매
    PAYMENT_ERROR,       // 결제 오류
    ADMIN_REQUEST,       // 관리자 취소
    SYSTEM_ERROR         // 시스템 오류
}
