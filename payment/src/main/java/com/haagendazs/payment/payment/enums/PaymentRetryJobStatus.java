package com.haagendazs.payment.payment.enums;

public enum PaymentRetryJobStatus {
    SCHEDULED, // 실행 예정
    RUNNING, // 실행 중
    SUCCEEDED, // 재시도 성공
    FAILED, // 최종 실패
    CANCELED // 취소됨
}
