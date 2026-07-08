package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.global.BaseEntity;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.CardType;
import com.haagendazs.payment.payment.enums.OwnerType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "billing")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Billing extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //회원id
    @Column(nullable = false)
    private Long memberId;

    //자동결제키
    @Column(nullable = false)
    private String billingKey;

    //카드사코드
    @Enumerated(EnumType.STRING)
    private CardCompany issuerCode;

    //카드번호
    private String cardNumber;

    //카드타입
    @Enumerated(EnumType.STRING)
    private CardType cardType;

    //오너타입
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;

    //기본결제수단여부
    //private Boolean isDefault;

    //결제수단상태
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BillingStatus billingStatus;

}
