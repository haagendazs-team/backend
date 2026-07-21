package com.haagendazs.payment.subscription.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.order.service.dto.OrderCreateResponse;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.TossPaymentErrorAction;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.service.BillingPaymentService;
import com.haagendazs.payment.payment.service.PaymentRetryJobService;
import com.haagendazs.payment.payment.service.tools.TossPaymentException;
import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductType;
import com.haagendazs.payment.product.repository.ProductsRepository;
import com.haagendazs.payment.subscription.entity.SubscriptionPlan;
import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.enums.PlanType;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import com.haagendazs.payment.subscription.repository.SubscriptionPlanRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalService {

    private final SubscriptionsRepository subscriptionsRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;
    private final ProductsRepository productsRepository;
    private final BillingRepository billingRepository;
    private final OrderService orderService;
    private final BillingPaymentService billingPaymentService;
    private final PaymentRetryJobService paymentRetryJobService;
    private final SubscriptionService subscriptionService;

    public void renewDueSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        SubscriptionPlan standardPlan = subscriptionPlanRepository.findByType(PlanType.STANDARD)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        renewStandardSubscriptions(now, standardPlan);
        renewPaidSubscriptions(now, standardPlan);
    }

    private void renewStandardSubscriptions(
            LocalDateTime now,
            SubscriptionPlan standardPlan
    ) {
        subscriptionsRepository
                .findRenewalDueStandardSubscriptions(now, standardPlan.getId())
                .forEach(subscription -> subscriptionService.renewSubscriptionPeriod(
                        subscription.getWorkspaceId(),
                        standardPlan.getId(),
                        null,
                        now
                ));
    }

    private void renewPaidSubscriptions(
            LocalDateTime now,
            SubscriptionPlan standardPlan
    ) {
        subscriptionsRepository
                .findRenewalDuePaidSubscriptions(now, standardPlan.getId())
                .forEach(this::renewPaidSubscription);
    }

    private void renewPaidSubscription(Subscriptions subscription) {
        Billing billing = null;
        OrderCreateResponse order = null;

        try {
            SubscriptionPlan plan = findRenewalPlan(subscription);
            Products product = productsRepository.findByProductTypeAndProductDetailId(
                            ProductType.SUBSCRIPTION,
                            plan.getId()
                    )
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));
            billing = getActiveBilling(subscription.getBillingId());

            subscriptionService.markRenewalPending(subscription.getWorkspaceId());

            order = orderService.createSubscriptionOrderWithAmount(
                    billing.getMemberId(),
                    subscription.getWorkspaceId(),
                    product.getId(),
                    product.getPrice()
            );

            billingPaymentService.paySubscriptionRenewalWithBillingMethod(
                    billing.getMemberId(),
                    order.orderNo(),
                    billing
            );
        } catch (TossPaymentException e) {
            handleTossRenewalFailure(subscription, billing, order, e);
        } catch (RuntimeException e) {
            markPastDueAndCancelScheduledPlanChanges(subscription);
            log.warn(
                    "구독 갱신 자동결제 처리 실패 subscriptionId={} workspaceId={}",
                    subscription.getId(),
                    subscription.getWorkspaceId(),
                    e
            );
        }
    }

    private SubscriptionPlan findRenewalPlan(Subscriptions subscription) {
        Long renewalPlanId = subscriptionScheduledChangesRepository
                .findFirstBySubscriptionIdAndChangeTypeAndChangeStatusOrderByScheduledAtDesc(
                        subscription.getId(),
                        SubscriptionChangeType.PLAN_CHANGE,
                        SubscriptionChangeStatus.SCHEDULED
                )
                .filter(change -> !change.getScheduledAt().isAfter(LocalDateTime.now()))
                .map(SubscriptionScheduledChanges::getRequestedPlanId)
                .orElse(subscription.getSubscriptionPlanId());

        return subscriptionPlanRepository.findById(renewalPlanId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));
    }

    private void handleTossRenewalFailure(
            Subscriptions subscription,
            Billing billing,
            OrderCreateResponse order,
            TossPaymentException exception
    ) {
        if (exception.getTossPaymentErrorCode().getAction() != TossPaymentErrorAction.RETRY_LATER) {
            markPastDueAndCancelScheduledPlanChanges(subscription);
            log.warn(
                    "구독 갱신 자동결제 실패 subscriptionId={} workspaceId={} tossCode={}",
                    subscription.getId(),
                    subscription.getWorkspaceId(),
                    exception.getTossCode()
            );
            return;
        }

        if (billing == null || order == null) {
            markPastDueAndCancelScheduledPlanChanges(subscription);
            log.warn(
                    "구독 갱신 자동결제 재시도 예약 실패 subscriptionId={} workspaceId={} reason=missing_order_or_billing",
                    subscription.getId(),
                    subscription.getWorkspaceId()
            );
            return;
        }

        try {
            paymentRetryJobService.scheduleRetry(
                    billing.getMemberId(),
                    order.orderNo(),
                    billing.getId(),
                    exception
            );
        } catch (RuntimeException handlingException) {
            markPastDueAndCancelScheduledPlanChanges(subscription);
            log.warn(
                    "구독 갱신 자동결제 실패 후 재시도 예약 처리 실패 subscriptionId={} workspaceId={}",
                    subscription.getId(),
                    subscription.getWorkspaceId(),
                    handlingException
            );
        }
    }

    private void markPastDueAndCancelScheduledPlanChanges(Subscriptions subscription) {
        try {
            subscriptionService.markPastDueAndCancelScheduledPlanChanges(subscription.getWorkspaceId());
        } catch (RuntimeException e) {
            log.warn(
                    "구독 연체 및 플랜 변경 예약 취소 실패 subscriptionId={} workspaceId={}",
                    subscription.getId(),
                    subscription.getWorkspaceId(),
                    e
            );
        }
    }

    private Billing getActiveBilling(Long billingId) {
        return billingRepository.findByIdAndBillingStatus(
                        billingId,
                        BillingStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_METHOD_NOT_FOUND));
    }
}
