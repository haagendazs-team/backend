package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.infrastructure.kafka.PaymentEventProducer;
import com.haagendazs.infrastructure.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscriptionExpiredEvent;
import com.haagendazs.domain.model.SubscriptionPeriods;
import com.haagendazs.domain.model.SubscriptionPlan;
import com.haagendazs.domain.model.SubscriptionScheduledChanges;
import com.haagendazs.domain.model.Subscriptions;
import com.haagendazs.domain.model.PlanType;
import com.haagendazs.domain.model.SubscriptionChangeStatus;
import com.haagendazs.domain.model.SubscriptionChangeType;
import com.haagendazs.domain.model.SubscriptionStatus;
import com.haagendazs.domain.repository.SubscriptionPeriodsRepository;
import com.haagendazs.domain.repository.SubscriptionPlanRepository;
import com.haagendazs.domain.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.domain.repository.SubscriptionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionExpirationService {

    private final SubscriptionsRepository subscriptionsRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPeriodsRepository subscriptionPeriodsRepository;
    private final SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public void expirePaidSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        applyScheduledPlanChanges(now);
    }

    private void applyScheduledPlanChanges(LocalDateTime now) {
        subscriptionScheduledChangesRepository
                .findByChangeTypeAndChangeStatusAndScheduledAtLessThanEqual(
                        SubscriptionChangeType.PLAN_CHANGE,
                        SubscriptionChangeStatus.SCHEDULED,
                        now
                )
                .forEach(scheduledChange -> applyScheduledPlanChange(scheduledChange, now));
    }

    private void applyScheduledPlanChange(
            SubscriptionScheduledChanges scheduledChange,
            LocalDateTime now
    ) {
        Subscriptions subscription = subscriptionsRepository.findById(scheduledChange.getSubscriptionId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND));

        SubscriptionPlan targetPlan = subscriptionPlanRepository.findById(scheduledChange.getRequestedPlanId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        // 유료 플랜 변경은 갱신 자동결제가 성공한 뒤 적용한다.
        if (targetPlan.getType() != PlanType.STANDARD) {
            return;
        }

        applyPlan(subscription, targetPlan, now);
        publishWorkspaceSubscribed(subscription, targetPlan, now);
        scheduledChange.apply();
    }

    private void expireSubscriptionsWithoutScheduledChange(LocalDateTime now) {
        SubscriptionPlan standardPlan = subscriptionPlanRepository.findByType(PlanType.STANDARD)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        subscriptionsRepository.findExpiredPaidSubscriptions(now, standardPlan.getId())
                .forEach(subscription -> {
                    applyPlan(subscription, standardPlan, now);
                    publishWorkspaceSubscriptionExpired(subscription, standardPlan, now);
                });
    }

    private void applyPlan(
            Subscriptions subscription,
            SubscriptionPlan plan,
            LocalDateTime periodStart
    ) {
        LocalDateTime periodEnd = periodStart.plusDays(plan.getDurationDays());
        Long billingId = plan.getType() == PlanType.STANDARD ? null : subscription.getBillingId();

        subscription.updatePlan(
                plan.getId(),
                periodStart,
                periodEnd,
                billingId
        );

        subscriptionPeriodsRepository.save(
                SubscriptionPeriods.builder()
                        .planId(plan.getId())
                        .workspaceId(subscription.getWorkspaceId())
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .status(SubscriptionStatus.ACTIVE)
                        .build()
        );

        paymentEventProducer.publishSubscriptionChanged(
                new SubscriptionChangedEvent(
                        subscription.getWorkspaceId(),
                        plan.getSearchableDays(),
                        LocalDateTime.now()
                )
        );
    }

    private void publishWorkspaceSubscribed(
            Subscriptions subscription,
            SubscriptionPlan plan,
            LocalDateTime activatedAt
    ) {
        paymentEventProducer.publishWorkspaceSubscribed(
                new WorkspaceSubscribedEvent(
                        subscription.getWorkspaceId(),
                        plan.getType().name(),
                        activatedAt
                )
        );
    }

    private void publishWorkspaceSubscriptionExpired(
            Subscriptions subscription,
            SubscriptionPlan plan,
            LocalDateTime expiredAt
    ) {
        paymentEventProducer.publishWorkspaceSubscriptionExpired(
                new WorkspaceSubscriptionExpiredEvent(
                        subscription.getWorkspaceId(),
                        plan.getType().name(),
                        expiredAt
                )
        );
    }
}
