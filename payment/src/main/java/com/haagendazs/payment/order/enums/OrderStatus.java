package com.haagendazs.payment.order.enums;

public enum OrderStatus {
    PENDING, //결제대기
    PAID, //결제
    CANCELED, //취소
    EXPIRED, //결제제한시간만료
    FAILED //실패
}
