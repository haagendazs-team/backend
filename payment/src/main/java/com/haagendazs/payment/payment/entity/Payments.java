package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.global.BaseEntity;
import com.haagendazs.payment.payment.enums.*;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "payments")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payments extends BaseEntity {

    //결제id
    @Id
    @GeneratedValue
    private Long id;

    //주문id
    @Column(nullable = false)
    private Long orderId;

    //PG사 결제 식별자
    @Column(nullable = true)
    private String paymentKey;

    //결제수단
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    //PG사 또는 간편결제 제공자
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider paymentProvider;

    //결제 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    //승인 금액
    @Column(nullable = false)
    private Long totalAmount;

    //실패코드
    @Column(nullable = true)
    private String failCode;

    //승인완료시각
    @Column(nullable = true)
    private LocalDateTime approvedAt;

    //카드사
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private CardCompany cardCompany;

    //카드번호
    @Column(nullable = true)
    private String cardNumber;

    //간편결제사
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private EasyPayProvider easyPayProvider;

    //영수증 url
    @Column(nullable = true)
    private String receiptUrl;

}
