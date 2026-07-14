package com.haagendazs.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.TestPaymentApplication;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.payment.global.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.order.service.dto.OrderCreateResponse;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.product.entity.OrderItems;
import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductStatus;
import com.haagendazs.payment.product.enums.ProductType;
import com.haagendazs.payment.product.repository.ProductsRepository;
import com.haagendazs.payment.subscription.entity.SubscriptionPeriods;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.enums.PlanType;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeAction;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import com.haagendazs.payment.subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.subscription.repository.SubscriptionPeriodsRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionPlanRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionRequest;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionResponse;
import com.haagendazs.payment.subscription.service.dto.GetSubscriptionResponse;
import com.haagendazs.payment.subscription.service.dto.GetsubscriptionPeriodsResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestPaymentApplication.class)
@Transactional
@ActiveProfiles("test")
public class SubscriptionServiceTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionsRepository subscriptionsRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionPeriodsRepository subscriptionPeriodsRepository;

    @Autowired
    private SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;

    @Autowired
    private ProductsRepository productsRepository;

    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("워크스페이스 생성시 구독플랜 설정 - succes")
    void createDefaultSubscriptionTest() {//createDefaultSubscriptionIfAbsent 메서드 테스트
        subscriptionService.createDefaultSubscriptionIfAbsent(1L);

        Optional<Subscriptions> subscriptions = subscriptionsRepository.findByWorkspaceId(1L);

        assertThat(subscriptions.get().getSubscriptionPlanId()).isEqualTo(1L);
        assertThat(subscriptions.get().getWorkspaceId()).isEqualTo(1L);
        assertThat(subscriptions.get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    //createDefaultSubscriptionIfAbsent 메서드 테스트
    //이미 있으면 예외를 던지지 않고 '그냥 지나감'
    @Test
    @DisplayName("워크스페이스 생성시 구독플랜 설정 - fail1")
    void createDefaultSubscriptionFailTest() {
        LocalDateTime now = LocalDateTime.now();
        Long workspaceId = 2L;
        Long subscriptionPlanId = 3L;

        Subscriptions newSub = Subscriptions.builder()
                .subscriptionPlanId(subscriptionPlanId)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusDays(30))
                .build();

        subscriptionsRepository.saveAndFlush(newSub);

        assertThat(subscriptionsRepository.existsByWorkspaceId(workspaceId)).isTrue();

        subscriptionService.createDefaultSubscriptionIfAbsent(workspaceId);

        Optional<Subscriptions> subscriptions = subscriptionsRepository.findByWorkspaceId(workspaceId);

        assertThat(subscriptions.get().getSubscriptionPlanId()).isEqualTo(subscriptionPlanId);
        assertThat(subscriptions.get().getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(subscriptions.get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    //createDefaultSubscriptionIfAbsent 메서드 테스트
    //기본 플랜 없음
    @Test
    @DisplayName("워크스페이스 생성시 구독플랜 설정 - fail2")
    void createDefaultSubscriptionPlanNotFoundTest() {
        Long workspaceId = 999L;

        subscriptionPlanRepository.deleteById(1L);

        assertThatThrownBy(() -> subscriptionService.createDefaultSubscriptionIfAbsent(workspaceId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PLAN_NOT_FOUND);

        assertThat(subscriptionsRepository.findByWorkspaceId(workspaceId)).isEmpty();
    }

    //activateSubscriptionByPayment 메서드 테스트
    @Test
    @DisplayName("구독 결제시 구독플랜 설정 - succes")
    void activateSubscriptionByPaymentTest() {
        Long workspaceId = 10L;
        Billing billing = billing(100L, 3L);
        Orders order = subscriptionOrder(3L, workspaceId, 2L);

        subscriptionService.activateSubscriptionByPayment(order, billing);

        Subscriptions subscription = subscriptionsRepository.findByWorkspaceId(workspaceId).get();
        assertThat(subscription.getSubscriptionPlanId()).isEqualTo(2L);
        assertThat(subscription.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getBillingId()).isEqualTo(billing.getId());

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(workspaceId))
                .singleElement()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(2L);
                    assertThat(period.getWorkspaceId()).isEqualTo(workspaceId);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                });

        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    //activateSubscriptionByPayment 메서드 테스트
    @Test
    @DisplayName("구독 결제시 기존 구독을 상위 플랜으로 변경")
    void activateSubscriptionByPaymentUpgradeTest() {
        Long workspaceId = 11L;
        Billing billing = billing(101L, 4L);
        Orders order = subscriptionOrder(4L, workspaceId, 3L);
        Subscriptions existingSubscription = Subscriptions.builder()
                .subscriptionPlanId(1L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(364))
                .build();
        subscriptionsRepository.saveAndFlush(existingSubscription);

        subscriptionService.activateSubscriptionByPayment(order, billing);

        Subscriptions subscription = subscriptionsRepository.findByWorkspaceId(workspaceId).get();
        assertThat(subscription.getId()).isEqualTo(existingSubscription.getId());
        assertThat(subscription.getSubscriptionPlanId()).isEqualTo(3L);
        assertThat(subscription.getBillingId()).isEqualTo(billing.getId());

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(workspaceId))
                .singleElement()
                .extracting(SubscriptionPeriods::getPlanId)
                .isEqualTo(3L);

        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    //activateSubscriptionByPayment 메서드 테스트
    @Test
    @DisplayName("구독 결제시 기존 구독보다 낮거나 같은 플랜이면 예외 발생")
    void activateSubscriptionByPaymentInvalidChangeTest() {
        Long workspaceId = 12L;
        Billing billing = billing(102L, 5L);
        Orders order = subscriptionOrder(5L, workspaceId, 2L);
        Subscriptions existingSubscription = Subscriptions.builder()
                .subscriptionPlanId(3L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build();
        subscriptionsRepository.saveAndFlush(existingSubscription);

        assertThatThrownBy(() -> subscriptionService.activateSubscriptionByPayment(order, billing))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_SUBSCRIPTION_CHANGE);

        Subscriptions subscription = subscriptionsRepository.findByWorkspaceId(workspaceId).get();
        assertThat(subscription.getSubscriptionPlanId()).isEqualTo(3L);
        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(workspaceId)).isEmpty();
        verify(paymentEventProducer, never()).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer, never()).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    private Orders subscriptionOrder(Long memberId, Long workspaceId, Long productId) {
        Orders order = Orders.builder()
                .memberId(memberId)
                .workspaceId(workspaceId)
                .orderNo("ORDER-" + workspaceId + "-" + productId)
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PAID)
                .orderType(OrderType.Billing)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();

        order.addOrderItem(OrderItems.builder()
                .productId(productId)
                .itemName("Subscription")
                .itemType("SUBSCRIPTION")
                .unitPrice(19900L)
                .quantity(1L)
                .totalPrice(19900L)
                .build());

        return order;
    }

    private Billing billing(Long billingId, Long memberId) {
        return Billing.builder()
                .id(billingId)
                .memberId(memberId)
                .billingStatus(BillingStatus.ACTIVE)
                .build();
    }

    //getWorkspaceSubscription 메서드 테스트
    @Test
    @DisplayName("워크스페이스의 현재 구독 확인 - succes")
    void getWorkspaceSubscriptionTest() {
        Long workspaceId = 20L;
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(30);
        Subscriptions subscription = subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(2L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now())
                .currentPeriodEnd(periodEnd)
                .build());

        GetSubscriptionResponse response = subscriptionService.getWorkspaceSubscription(workspaceId);

        assertThat(response.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(response.planId()).isEqualTo(2L);
        assertThat(response.planName()).isEqualTo("Plus Plan");
        assertThat(response.planType()).isEqualTo(PlanType.PLUS);
        assertThat(response.periodEnd()).isEqualTo(periodEnd);
    }

    //getWorkspaceSubscription 메서드 테스트
    @Test
    @DisplayName("워크스페이스의 현재 구독 확인 - 구독 없음")
    void getWorkspaceSubscriptionNotFoundTest() {
        Long workspaceId = 21L;

        assertThatThrownBy(() -> subscriptionService.getWorkspaceSubscription(workspaceId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    //getWorkspaceSubscription 메서드 테스트
    @Test
    @DisplayName("워크스페이스의 현재 구독 확인 - 플랜 없음")
    void getWorkspaceSubscriptionPlanNotFoundTest() {
        Long workspaceId = 22L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(999L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now())
                .currentPeriodEnd(LocalDateTime.now().plusDays(30))
                .build());

        assertThatThrownBy(() -> subscriptionService.getWorkspaceSubscription(workspaceId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PLAN_NOT_FOUND);
    }

    //getSubscriptionPeriods 메서드 테스트
    @Test
    @DisplayName("워크스페이스의 구독 기간 이력 확인 - succes")
    void getSubscriptionPeriodsTest() {
        Long workspaceId = 30L;
        LocalDateTime oldStart = LocalDateTime.now().minusDays(60);
        LocalDateTime oldEnd = LocalDateTime.now().minusDays(30);
        LocalDateTime recentStart = LocalDateTime.now().minusDays(30);
        LocalDateTime recentEnd = LocalDateTime.now();
        subscriptionPeriodsRepository.save(SubscriptionPeriods.builder()
                .planId(1L)
                .workspaceId(workspaceId)
                .periodStart(oldStart)
                .periodEnd(oldEnd)
                .status(SubscriptionStatus.ACTIVE)
                .build());
        subscriptionPeriodsRepository.saveAndFlush(SubscriptionPeriods.builder()
                .planId(2L)
                .workspaceId(workspaceId)
                .periodStart(recentStart)
                .periodEnd(recentEnd)
                .status(SubscriptionStatus.ACTIVE)
                .build());

        List<GetsubscriptionPeriodsResponse> responses = subscriptionService.getSubscriptionPeriods(workspaceId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).planName()).isEqualTo("Plus Plan");
        assertThat(responses.get(0).periodStart()).isEqualTo(recentStart);
        assertThat(responses.get(0).periodEnd()).isEqualTo(recentEnd);
        assertThat(responses.get(1).planName()).isEqualTo("Standard Plan");
        assertThat(responses.get(1).periodStart()).isEqualTo(oldStart);
        assertThat(responses.get(1).periodEnd()).isEqualTo(oldEnd);
    }

    //getSubscriptionPeriods 메서드 테스트
    @Test
    @DisplayName("워크스페이스의 구독 기간 이력 확인 - 이력 없음")
    void getSubscriptionPeriodsEmptyTest() {
        Long workspaceId = 31L;

        List<GetsubscriptionPeriodsResponse> responses = subscriptionService.getSubscriptionPeriods(workspaceId);

        assertThat(responses).isEmpty();
    }

    //getSubscriptionPeriods 메서드 테스트
    @Test
    @DisplayName("워크스페이스의 구독 기간 이력 확인 - 플랜 없음")
    void getSubscriptionPeriodsPlanNotFoundTest() {
        Long workspaceId = 32L;
        subscriptionPeriodsRepository.saveAndFlush(SubscriptionPeriods.builder()
                .planId(999L)
                .workspaceId(workspaceId)
                .periodStart(LocalDateTime.now().minusDays(30))
                .periodEnd(LocalDateTime.now())
                .status(SubscriptionStatus.ACTIVE)
                .build());

        assertThatThrownBy(() -> subscriptionService.getSubscriptionPeriods(workspaceId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PLAN_NOT_FOUND);
    }

    //changeSubscription 메서드 테스트
    @Test
    @DisplayName("워크스페이스 구독 변경 - 상위 플랜 변경시 추가 결제 주문 생성")
    void changeSubscriptionUpgradeTest(){
        Long workspaceId = 40L;
        Long memberId = 40L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(2L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());
        when(orderService.createSubscriptionOrderWithAmount(eq(memberId), eq(workspaceId), eq(3L), eq(10000L)))
                .thenReturn(new OrderCreateResponse(
                        500L,
                        "ORDER-500",
                        "Pro Subscription",
                        10000L,
                        OrderType.Billing,
                        "customer-40"
                ));

        ChangeSubscriptionResponse response =
                subscriptionService.changeSubscription(memberId, workspaceId, new ChangeSubscriptionRequest(3L));

        assertThat(response.action()).isEqualTo(SubscriptionChangeAction.PAYMENT_REQUIRED);
        assertThat(response.orderId()).isEqualTo(500L);
        assertThat(response.orderNo()).isEqualTo("ORDER-500");
        assertThat(response.orderName()).isEqualTo("Pro Subscription");
        assertThat(response.amount()).isEqualTo(10000L);
        assertThat(response.orderType()).isEqualTo(OrderType.Billing);
        assertThat(response.customerKey()).isEqualTo("customer-40");
        verify(orderService).createSubscriptionOrderWithAmount(memberId, workspaceId, 3L, 10000L);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 하위 플랜 변경시 다음 기간 예약")
    void changeSubscriptionDowngradeScheduledTest(){
        Long workspaceId = 41L;
        Long memberId = 41L;
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(10);
        Subscriptions subscription = subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(3L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(20))
                .currentPeriodEnd(periodEnd)
                .build());

        ChangeSubscriptionResponse response =
                subscriptionService.changeSubscription(memberId, workspaceId, new ChangeSubscriptionRequest(2L));

        assertThat(response.action()).isEqualTo(SubscriptionChangeAction.SCHEDULED);
        assertThat(response.orderId()).isNull();
        assertThat(subscriptionScheduledChangesRepository.findAll())
                .singleElement()
                .satisfies(scheduledChange -> {
                    assertThat(scheduledChange.getMemberId()).isEqualTo(memberId);
                    assertThat(scheduledChange.getSubscriptionId()).isEqualTo(subscription.getId());
                    assertThat(scheduledChange.getRequestedPlanId()).isEqualTo(2L);
                    assertThat(scheduledChange.getChangeType()).isEqualTo(SubscriptionChangeType.PLAN_CHANGE);
                    assertThat(scheduledChange.getChangeStatus()).isEqualTo(SubscriptionChangeStatus.SCHEDULED);
                    assertThat(scheduledChange.getScheduledAt()).isEqualTo(periodEnd);
                });
        verify(orderService, never()).createSubscriptionOrderWithAmount(any(), any(), any(), any());
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 구독 없음")
    void changeSubscriptionSubscriptionNotFoundTest(){
        assertThatThrownBy(() -> subscriptionService.changeSubscription(42L, 42L, new ChangeSubscriptionRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 현재 플랜 없음")
    void changeSubscriptionCurrentPlanNotFoundTest(){
        Long workspaceId = 43L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(999L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());

        assertThatThrownBy(() -> subscriptionService.changeSubscription(43L, workspaceId, new ChangeSubscriptionRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 대상 상품 없음")
    void changeSubscriptionProductNotFoundTest(){
        Long workspaceId = 44L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(1L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());

        assertThatThrownBy(() -> subscriptionService.changeSubscription(44L, workspaceId, new ChangeSubscriptionRequest(999L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 판매 중지 상품")
    void changeSubscriptionSuspendedProductTest(){
        Long workspaceId = 45L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(1L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());
        Products product = productsRepository.saveAndFlush(Products.builder()
                .name("Suspended Plus Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(19900L)
                .status(ProductStatus.SUSPENDED)
                .product_detail_id(2L)
                .build());

        assertThatThrownBy(() -> subscriptionService.changeSubscription(45L, workspaceId, new ChangeSubscriptionRequest(product.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_SUSPENDED);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 대상 플랜 없음")
    void changeSubscriptionTargetPlanNotFoundTest(){
        Long workspaceId = 46L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(1L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());
        Products product = productsRepository.saveAndFlush(Products.builder()
                .name("Unknown Plan Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(19900L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(999L)
                .build());

        assertThatThrownBy(() -> subscriptionService.changeSubscription(46L, workspaceId, new ChangeSubscriptionRequest(product.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 현재 플랜 상품 없음")
    void changeSubscriptionCurrentProductNotFoundTest(){
        Long workspaceId = 47L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(1L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());
        productsRepository.deleteById(1L);
        productsRepository.flush();

        assertThatThrownBy(() -> subscriptionService.changeSubscription(47L, workspaceId, new ChangeSubscriptionRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 같은 플랜 변경 불가")
    void changeSubscriptionSamePlanTest(){
        Long workspaceId = 48L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(2L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());

        assertThatThrownBy(() -> subscriptionService.changeSubscription(48L, workspaceId, new ChangeSubscriptionRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_SUBSCRIPTION_CHANGE);
        verify(orderService, never()).createSubscriptionOrderWithAmount(any(), any(), any(), any());
    }

    @Test
    @DisplayName("워크스페이스 구독 변경 - 상위 플랜 추가 결제 금액 없음")
    void changeSubscriptionUpgradeAmountNotPositiveTest(){
        Long workspaceId = 49L;
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(1L)
                .workspaceId(workspaceId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(29))
                .build());
        Products product = productsRepository.saveAndFlush(Products.builder()
                .name("Free Plus Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(0L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(2L)
                .build());

        assertThatThrownBy(() -> subscriptionService.changeSubscription(49L, workspaceId, new ChangeSubscriptionRequest(product.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_SUBSCRIPTION_CHANGE);
        verify(orderService, never()).createSubscriptionOrderWithAmount(any(), any(), any(), any());
    }
}
