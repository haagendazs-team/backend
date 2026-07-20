package com.haagendazs.payment.global;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.payment.global.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.payment.global.kafka.dto.WorkspaceSubscriptionExpiredEvent;
import com.haagendazs.payment.subscription.entity.SubscriptionPeriods;
import com.haagendazs.payment.subscription.entity.SubscriptionPlan;
import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.enums.PlanType;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import com.haagendazs.payment.subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.subscription.repository.SubscriptionPeriodsRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionPlanRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
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
