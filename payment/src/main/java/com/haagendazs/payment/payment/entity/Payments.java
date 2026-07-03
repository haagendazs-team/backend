package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.global.BaseEntity;
import com.haagendazs.payment.payment.enums.*;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Entity
@Table(name = "payments")
@Getter
@Builder
@AllArgsConstructor
public class Payments extends BaseEntity {

    //결제id
    @Id
    @GeneratedValue
    private Long id;

    //주문id
    private Long orderId;

    //PG사 결제 식별자
    private String paymentKey;

    //결제수단
    private PaymentMethod paymentMethod;

    //PG사 또는 간편결제 제공자
    private PaymentProvider paymentProvider;

    //결제 상태
    private PaymentStatus paymentStatus;

    //승인 금액
    private Long totalAmount;

    //취소된 금액
    private Long cancelAmount;

    //실패코드
    private FailCode failCode;

    //실패사유
    private String failReason;

    //결제승인요청시각
    private LocalDateTime requested_at;

    //승인완료시각
    private LocalDateTime approved_at;

    //실패시각
    private LocalDateTime failedAt;

    //취소시각
    private LocalDateTime canceledAt;

    //카드사
    private CardCompany cardCompany;

    //카드번호
    private String cardNumber;

    //간편결제사
    private EasyPayProvider easyPayProvider;

    //영수증 url
    private String receipt_url;

}
