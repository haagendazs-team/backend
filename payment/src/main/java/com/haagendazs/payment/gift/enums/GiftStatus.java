package com.haagendazs.payment.gift.enums;

public enum GiftStatus {
    READY,     // 선물 생성됨, 아직 발송 전
    SENT,      // 선물 발송 완료
    RECEIVED,  // 선물 수령 완료
    CANCELED   // 선물 취소
}
