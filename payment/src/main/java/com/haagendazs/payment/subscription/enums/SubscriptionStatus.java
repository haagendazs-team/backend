package com.haagendazs.payment.subscription.enums;

public enum SubscriptionStatus {
    ACTIVE,     // 구독 중
    RENEWAL_PENDING, // 갱신 결제 처리 중
    PAST_DUE, // 갱신 결제 실패 후 유예/재시도 중
    EXPIRED, // 갱신 최종 실패 또는 만료
    //CANCELED,   // 구독 취소
    SUSPENDED   // 구독 정지
}
