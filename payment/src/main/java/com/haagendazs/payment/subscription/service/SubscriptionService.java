package com.haagendazs.payment.subscription.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.payment.global.kafka.dto.SubscriptionInitializedEvent;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.order.service.dto.OrderCreateResponse;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductStatus;
import com.haagendazs.payment.product.enums.ProductType;
import com.haagendazs.payment.product.repository.ProductsRepository;
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

import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionRequest;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionResponse;
import com.haagendazs.payment.subscription.service.dto.GetSubscriptionResponse;

import com.haagendazs.payment.subscription.service.dto.GetsubscriptionPeriodsResponse;

import java.time.LocalDateTime;

import java.util.List;
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
    private final SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;
    private final ProductsRepository productsRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final OrderService orderService;

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

            if (comparePlanRank(newPlan.getType(), currentPlan.getType()) <= 0) {
                throw new BusinessException(PaymentErrorCode.INVALID_SUBSCRIPTION_CHANGE);
            }

            // 기존 구독 정보 수정
            existingSubscription.updatePlan(
                    newPlan.getId(),
                    now,
                    now.plusDays(newPlan.getDurationDays()),
                    billing.getId()
            );
        } else {
            // [케이스 B] 기존 구독이 전혀 없는 경우 -> 최초 생성
            Subscriptions newSubscription = Subscriptions.builder()
                    .subscriptionPlanId(newPlan.getId())
                    .workspaceId(orders.getWorkspaceId())
                    .status(SubscriptionStatus.ACTIVE)
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

    @Transactional(readOnly = true)
    public GetSubscriptionResponse getWorkspaceSubscription(Long workspaceId){
        Subscriptions subscriptionOpt =
                subscriptionsRepository.findByWorkspaceId(workspaceId)
                        .orElseThrow(() -> new BusinessException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND));

        SubscriptionPlan subscriptionPlan =
                subscriptionPlanRepository.findById(subscriptionOpt.getSubscriptionPlanId())
                        .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        return new GetSubscriptionResponse(
                subscriptionOpt.getId(),
                subscriptionPlan.getId(),
                subscriptionPlan.getName(),
                subscriptionPlan.getType(),
                subscriptionOpt.getCurrentPeriodEnd()
        );
    }

    @Transactional(readOnly = true)
    public List<GetsubscriptionPeriodsResponse> getSubscriptionPeriods(Long workspaceId){
        return subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(workspaceId)
                .stream()
                .map(this::toResponse)
                .toList();

    }

    @Transactional
    public ChangeSubscriptionResponse changeSubscription(
            Long memberId,
            Long workspaceId,
            ChangeSubscriptionRequest request
    ) {
        Subscriptions subscription = subscriptionsRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND));

        SubscriptionPlan currentPlan = subscriptionPlanRepository.findById(subscription.getSubscriptionPlanId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        Products targetProduct = productsRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));

        validateSubscriptionProduct(targetProduct);

        SubscriptionPlan targetPlan = subscriptionPlanRepository.findById(targetProduct.getProduct_detail_id())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        int comparison = comparePlanRank(targetPlan.getType(), currentPlan.getType());

        if (comparison == 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_SUBSCRIPTION_CHANGE);
        }

        if (comparison < 0) {
            schedulePlanChange(memberId, subscription, targetPlan);
            return ChangeSubscriptionResponse.scheduled();
        }

        Products currentProduct = productsRepository.findByProductTypeAndProductDetailId(
                        ProductType.SUBSCRIPTION,
                        currentPlan.getId()
                )
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));

        long upgradeAmount = targetProduct.getPrice() - currentProduct.getPrice();

        if (upgradeAmount <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_SUBSCRIPTION_CHANGE);
        }

        OrderCreateResponse order = orderService.createSubscriptionOrderWithAmount(
                memberId,
                workspaceId,
                targetProduct.getId(),
                upgradeAmount
        );

        return ChangeSubscriptionResponse.paymentRequired(
                order.orderId(),
                order.orderNo(),
                order.orderName(),
                order.amount(),
                order.orderType(),
                order.customerKey()
        );
    }

    private void validateSubscriptionProduct(Products product) {
        if (product.getStatus() == ProductStatus.SUSPENDED) {
            throw new BusinessException(PaymentErrorCode.PRODUCT_SUSPENDED);
        }
        if (product.getProductType() != ProductType.SUBSCRIPTION) {
            throw new BusinessException(PaymentErrorCode.UNSUPPORTED_PRODUCT_TYPE);
        }
    }

    private int comparePlanRank(PlanType targetPlanType, PlanType currentPlanType) {
        return Integer.compare(planRank(targetPlanType), planRank(currentPlanType));
    }

    private int planRank(PlanType planType) {
        return switch (planType) {
            case STANDARD -> 1;
            case PLUS -> 2;
            case PRO -> 3;
        };
    }

    private void schedulePlanChange(
            Long memberId,
            Subscriptions subscription,
            SubscriptionPlan targetPlan
    ) {
        SubscriptionScheduledChanges scheduledChange = SubscriptionScheduledChanges.builder()
                .memberId(memberId)
                .subscriptionId(subscription.getId())
                .requestedPlanId(targetPlan.getId())
                .changeType(SubscriptionChangeType.PLAN_CHANGE)
                .changeStatus(SubscriptionChangeStatus.SCHEDULED)
                .requestedAt(LocalDateTime.now())
                .scheduledAt(subscription.getCurrentPeriodEnd())
                .build();

        subscriptionScheduledChangesRepository.save(scheduledChange);
    }

    private GetsubscriptionPeriodsResponse toResponse(SubscriptionPeriods subscriptionPeriods) {
        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findById(subscriptionPeriods.getPlanId())
                .orElseThrow(()-> new BusinessException(PaymentErrorCode.PLAN_NOT_FOUND));

        return new GetsubscriptionPeriodsResponse(
                subscriptionPlan.getName(),
                subscriptionPeriods.getPeriodStart(),
                subscriptionPeriods.getPeriodEnd()
        );
    }
}
