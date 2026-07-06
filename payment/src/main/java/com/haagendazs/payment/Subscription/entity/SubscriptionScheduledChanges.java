package com.haagendazs.payment.Subscription.entity;

import com.haagendazs.payment.Subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.Subscription.enums.SubscriptionChangeType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(name = "subscription_scheduled_changes")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionScheduledChanges {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원 ID
    @Column(nullable = false)
    private Long memberId;

    // 현재 기간 구독 플랜 ID
    @Column(nullable = false)
    private Long subscriptionId;

    // 다음 기간 구독 플랜 ID
    @Column(nullable = false)
    private Long requestedPlanId;

    // 변경 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionChangeType changeType;

    // 변경 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionChangeStatus changeStatus;

    // 변경 요청 시각
    @Column(nullable = false)
    private LocalDateTime requestedAt;

    // 적용 예정 시각
    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    // 예약 취소 시각
    @Column(nullable = true)
    private LocalDateTime canceledAt;
}
