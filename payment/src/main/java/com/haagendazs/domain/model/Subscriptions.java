package com.haagendazs.domain.model;

import com.haagendazs.domain.model.SubscriptionStatus;
import com.haagendazs.domain.model.BaseEntity;
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

    // 현재 구독 기간 시작
    @Column(nullable = false)
    private LocalDateTime currentPeriodStart;

    // 현재 구독 기간 종료
    @Column(nullable = false)
    private LocalDateTime currentPeriodEnd;

    @Column
    private Long billingId;

    public void updatePlan(Long planId, LocalDateTime now, LocalDateTime localDateTime, Long billingId) {
        this.subscriptionPlanId = planId;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = localDateTime;
        this.billingId = billingId;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void markRenewalPending() {
        this.status = SubscriptionStatus.RENEWAL_PENDING;
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
    }
}
