package com.haagendazs.payment.subscription.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.payment.global.kafka.dto.SubscriptionInitializedEvent;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.product.entity.OrderItems;
import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.repository.ProductsRepository;
import com.haagendazs.payment.subscription.entity.SubscriptionPeriods;
import com.haagendazs.payment.subscription.entity.SubscriptionPlan;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.subscription.repository.SubscriptionPeriodsRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionPlanRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;

import java.time.LocalDateTime;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final SubscriptionPeriodsRepository subscriptionPeriodsRepository;
    private final ProductsRepository productsRepository;
    private final PaymentEventProducer paymentEventProducer;

    //워크스페이스 생성시 구독플랜 설정
    @Transactional
    public void createDefaultSubscriptionIfAbsent(
            Long workspaceId
    ) {
        // 1. 이미 구독이 존재하는지 확인
        boolean hasSubscription = subscriptionsRepository.existsByWorkspaceId(workspaceId);

        // 2. 이미 있으면 예외를 던지지 않고 '그냥 지나감'
        if (hasSubscription) {
            return;
        }

        //기본 플랜의 플랜 id
        Long defaultPlanId = 1L;

        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findById(defaultPlanId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        Subscriptions newSubscriptions = Subscriptions.builder()
                .subscriptionPlanId(subscriptionPlan.getId())
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusDays(subscriptionPlan.getDurationDays()))
                .build();

        Subscriptions saved = subscriptionsRepository.save(newSubscriptions);

        SubscriptionPeriods subscriptionPeriods = SubscriptionPeriods.builder()
                .planId(subscriptionPlan.getId())
                .workspaceId(workspaceId)
                .periodStart(saved.getCurrentPeriodStart())
                .periodEnd(saved.getCurrentPeriodEnd())
                .status(SubscriptionStatus.ACTIVE)
                .build();

        subscriptionPeriodsRepository.save(subscriptionPeriods);

        paymentEventProducer.publishSubscriptionInitialized(
                new SubscriptionInitializedEvent(
                        saved.getWorkspaceId(),
                        subscriptionPlan.getSearchableDays(),
                        LocalDateTime.now()
                )
        );
    }

    //최초 구독 결제시 구독플랜 설정
    public Void activateSubscriptionByPayment(
            Orders orders,
            Billing billing,
            Payments payments
    ){
        // 1. 주문 객체에서 구독 상품의 ID를 추출
        Long subscriptionProductId = orders.getSubscriptionProductId();

        // 2. 상품 테이블(Products)을 조회하여 플랜 ID(product_detail_id)를 가져옴
        Products product = productsRepository.findById(subscriptionProductId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));
        Long newPlanId = product.getProduct_detail_id();

        // 3. 플랜 상세 정보 조회
        SubscriptionPlan newPlan = subscriptionPlanRepository.findById(newPlanId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        // 4. 기존 구독 존재 여부 확인
        Optional<Subscriptions> subscriptionOpt =
                subscriptionsRepository.findByWorkspaceId(orders.getWorkspaceId());

        LocalDateTime now = LocalDateTime.now();

        if (subscriptionOpt.isPresent()) {
            // [케이스 A] 기존 구독이 존재하는 경우
            Subscriptions existingSubscription = subscriptionOpt.get();

            SubscriptionPlan currentPlan = subscriptionPlanRepository.findById(existingSubscription.getSubscriptionPlanId())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

            if (!"NORMAL".equals(currentPlan.getType())) {
                throw new BusinessException(PaymentErrorCode.ALREADY_PREMIUM_SUBSCRIPTION);
            }

            // 기존 구독 정보 수정
            existingSubscription.updatePlan(
                    newPlan.getId(),
                    now,
                    now.plusDays(newPlan.getDurationDays()),
                    existingSubscription.getBillingId()
            );
        } else {
            // [케이스 B] 기존 구독이 전혀 없는 경우 -> 최초 생성
            Subscriptions newSubscription = Subscriptions.builder()
                    .subscriptionPlanId(newPlan.getId())
                    .workspaceId(orders.getWorkspaceId())
                    .currentPeriodStart(now)
                    .currentPeriodEnd(now.plusDays(newPlan.getDurationDays()))
                    .billingId(billing.getId())
                    .build();

            subscriptionsRepository.save(newSubscription);
        }

        // 5. 공통 작업: 이력 관리를 위한 SubscriptionPeriods 저장
        SubscriptionPeriods subscriptionPeriods = SubscriptionPeriods.builder()
                .planId(newPlan.getId())
                .workspaceId(orders.getWorkspaceId())
                .periodStart(now)
                .periodEnd(now.plusDays(newPlan.getDurationDays()))
                .status(SubscriptionStatus.ACTIVE)
                .build();

        subscriptionPeriodsRepository.save(subscriptionPeriods);

        paymentEventProducer.publishSubscriptionChanged(
                new SubscriptionChangedEvent(
                        orders.getWorkspaceId(),
                        newPlan.getSearchableDays(),
                        LocalDateTime.now()
                )
        );

        return null;
    }
}
