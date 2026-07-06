package com.haagendazs.payment.Subscription.entity;

import com.haagendazs.payment.Subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.global.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "subscriptions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscriptions extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 구독 상품 ID
    @Column(nullable = false)
    private Long subscriptionPlanId;

    // 워크스페이스 ID
    @Column(nullable = false)
    private Long workspaceId;

    // 구독 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;

    // 구독 최초 시작 시각
    @Column(nullable = false)
    private LocalDateTime startedAt;

    // 현재 구독 기간 시작
    @Column(nullable = false)
    private LocalDateTime currentPeriodStart;

    // 현재 구독 기간 종료
    @Column(nullable = false)
    private LocalDateTime currentPeriodEnd;

    // 구독 취소 시각
    @Column(nullable = true)
    private LocalDateTime canceledAt;

}
