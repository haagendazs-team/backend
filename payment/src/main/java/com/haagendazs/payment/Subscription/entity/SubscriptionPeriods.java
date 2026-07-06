package com.haagendazs.payment.Subscription.entity;

import com.haagendazs.payment.Subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.global.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "subscription_periods")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPeriods extends BaseEntity {//구독기간이력

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //구독플랜id
    @Column(nullable = false)
    private Long planId;

    //워크스페이스id
    @Column(nullable = false)
    private Long workspaceId;

    //부여된 기간 시작
    @Column(nullable = false)
    private LocalDateTime periodStart;

    //부여된 기간 종료
    @Column(nullable = false)
    private LocalDateTime periodEnd;

    //기간상태
    @Column(nullable = false)
    private SubscriptionStatus status;
}
