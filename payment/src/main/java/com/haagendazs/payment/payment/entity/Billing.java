package com.haagendazs.payment.payment.entity;

import com.haagendazs.payment.global.BaseEntity;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.CardType;
import com.haagendazs.payment.payment.enums.OrnerType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "billing")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //회원id
    @Column(nullable = false)
    private Long memberId;

    //소비자 식별키
    @Column(nullable = false)
    private String customerKey;

    //자동결제키
    @Column(nullable = false)
    private String billingKey;

    //카드사코드
    private CardCompany issuerCode;

    //카드번호
    private String cardNumber;

    //카드타입
    private CardType cardType;

    //오너타입
    private OrnerType ornerType;

    //등록시간
    private LocalDateTime createdAt;

    //기본결제수단여부
    private Boolean isDefault;

    //결제수단상태
    private BillingStatus billingStatus;

}
