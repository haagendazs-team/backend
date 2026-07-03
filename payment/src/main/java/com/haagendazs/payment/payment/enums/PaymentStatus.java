package com.haagendazs.payment.payment.enums;

public enum PaymentStatus {
    READY,       // 결제 생성 후 인증 전 상태
    IN_PROGRESS,// 결제수단 인증 완료, 결제 승인 대기 상태
    DONE,        // 결제 승인 완료
    CANCELED,    // 결제 취소 완료
    ABORTED,     // 결제 승인 실패
    EXPIRED      // 결제 유효 시간 만료
}
