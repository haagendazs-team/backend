package com.haagendazs.payment.service;

import com.haagendazs.payment.TestPaymentApplication;
import com.haagendazs.payment.global.SubscriptionExpirationService;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.payment.global.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.repository.PaymentRepository;
import com.haagendazs.payment.payment.service.BillingPaymentService;
import com.haagendazs.payment.payment.service.dto.BillingPaymentRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import com.haagendazs.payment.payment.service.tools.TossPaymentException;
import com.haagendazs.payment.subscription.entity.SubscriptionPeriods;
import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeAction;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import com.haagendazs.payment.subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.subscription.repository.SubscriptionPeriodsRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
import com.haagendazs.payment.subscription.service.SubscriptionRenewalService;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionRequest;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestPaymentApplication.class)
@Transactional
@ActiveProfiles("test")
// Run: test 또는 ./gradlew :payment:test --tests com.haagendazs.payment.service.BillingSubscriptionUpgradeIntegrationTest
class BillingSubscriptionUpgradeIntegrationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long WORKSPACE_ID = 7001L;
    private static final Long STANDARD_PLAN_ID = 1L;
    private static final Long PLUS_PLAN_ID = 2L;
    private static final Long PLUS_PRODUCT_ID = 2L;
    private static final Long PRO_PLAN_ID = 3L;
    private static final Long PRO_PRODUCT_ID = 3L;
    private static final Long PRO_RENEWAL_AMOUNT = 29900L;
    private static final Long PLUS_RENEWAL_AMOUNT = 19900L;
    private static final Long UPGRADE_AMOUNT = PRO_RENEWAL_AMOUNT - PLUS_RENEWAL_AMOUNT;

    @Autowired
    private BillingPaymentService billingPaymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SubscriptionsRepository subscriptionsRepository;

    @Autowired
    private SubscriptionPeriodsRepository subscriptionPeriodsRepository;

    @Autowired
    private SubscriptionRenewalService subscriptionRenewalService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionExpirationService subscriptionExpirationService;

    @Autowired
    private SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;

    @MockitoBean
    private TossBillingClient tossBillingClient;

    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    @Test
    @DisplayName("Plus에서 Pro 업그레이드 시 플랜 가격 차액만 결제하고 구독이 변경된다")
    void payHigherSubscriptionPlan_thenSubscriptionPlanIsUpgraded() {
        Billing billing = defaultBilling();
        LocalDateTime previousPeriodStart = LocalDateTime.now().minusDays(10);
        LocalDateTime previousPeriodEnd = LocalDateTime.now().plusDays(20);
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PLUS_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(previousPeriodStart)
                .currentPeriodEnd(previousPeriodEnd)
                .billingId(billing.getId())
                .build());

        ChangeSubscriptionResponse changeResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PRO_PRODUCT_ID)
        );
        assertThat(changeResponse.action()).isEqualTo(SubscriptionChangeAction.PAYMENT_REQUIRED);
        assertThat(changeResponse.amount()).isEqualTo(UPGRADE_AMOUNT);
        assertThat(changeResponse.orderName()).isEqualTo("Pro Subscription");
        assertThat(changeResponse.orderType()).isEqualTo(OrderType.Billing);

        String orderNo = changeResponse.orderNo();
        Orders order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(order.getTotalAmount()).isEqualTo(UPGRADE_AMOUNT);
        assertThat(order.getSubscriptionProductId()).isEqualTo(PRO_PRODUCT_ID);
        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                eq(orderNo),
                eq(UPGRADE_AMOUNT),
                eq("Pro Subscription"),
                anyString()
        )).thenReturn(successResponse(orderNo));

        LocalDateTime beforePayment = LocalDateTime.now();
        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(orderNo)
        );

        Orders paidOrder = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(paidOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);

        Payments payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getTotalAmount()).isEqualTo(UPGRADE_AMOUNT);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-" + orderNo);

        Subscriptions subscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(subscription.getSubscriptionPlanId()).isEqualTo(PRO_PLAN_ID);
        assertThat(subscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforePayment);
        assertThat(subscription.getCurrentPeriodEnd()).isAfter(subscription.getCurrentPeriodStart());

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .first()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(PRO_PLAN_ID);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(period.getPeriodEnd()).isAfter(period.getPeriodStart());
                });
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    @Test
    @DisplayName("STANDARD 사용 중 Plus 업그레이드 시 19,900원을 결제하고 구독이 변경된다")
    void standardSubscriptionUpgradeToPlus_thenFullPlusPriceIsPaid() {
        Billing billing = defaultBilling();
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(STANDARD_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(30))
                .currentPeriodEnd(LocalDateTime.now().plusDays(335))
                .build());

        ChangeSubscriptionResponse changeResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PLUS_PRODUCT_ID)
        );
        assertThat(changeResponse.action()).isEqualTo(SubscriptionChangeAction.PAYMENT_REQUIRED);
        assertThat(changeResponse.amount()).isEqualTo(PLUS_RENEWAL_AMOUNT);
        assertThat(changeResponse.orderName()).isEqualTo("Plus Subscription");
        assertThat(changeResponse.orderType()).isEqualTo(OrderType.Billing);

        String orderNo = changeResponse.orderNo();
        Orders order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualTo(PLUS_RENEWAL_AMOUNT);
        assertThat(order.getSubscriptionProductId()).isEqualTo(PLUS_PRODUCT_ID);
        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                eq(orderNo),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
        )).thenReturn(successResponse(orderNo, PLUS_RENEWAL_AMOUNT, "Plus Subscription"));

        LocalDateTime beforePayment = LocalDateTime.now();
        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(orderNo)
        );

        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow())
                .satisfies(payment -> {
                    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
                    assertThat(payment.getTotalAmount()).isEqualTo(PLUS_RENEWAL_AMOUNT);
                    assertThat(payment.getPaymentKey()).isEqualTo("payment-key-" + orderNo);
                });

        Subscriptions upgradedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(upgradedSubscription.getSubscriptionPlanId()).isEqualTo(PLUS_PLAN_ID);
        assertThat(upgradedSubscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(upgradedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(upgradedSubscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforePayment);
        assertThat(upgradedSubscription.getCurrentPeriodEnd())
                .isEqualTo(upgradedSubscription.getCurrentPeriodStart().plusDays(30));

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(PLUS_PLAN_ID);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(period.getPeriodStart()).isEqualTo(upgradedSubscription.getCurrentPeriodStart());
                    assertThat(period.getPeriodEnd()).isEqualTo(upgradedSubscription.getCurrentPeriodEnd());
                });
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    @Test
    @DisplayName("결제 응답 시간이 초과되면 Toss 결제 상태를 확인해 Plus 업그레이드 DB를 보정한다")
    void billingPaymentReadTimeout_thenQueriesTossAndReconcilesSubscription() {
        Billing billing = defaultBilling();
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(STANDARD_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(30))
                .currentPeriodEnd(LocalDateTime.now().plusDays(335))
                .build());

        ChangeSubscriptionResponse changeResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PLUS_PRODUCT_ID)
        );
        String orderNo = changeResponse.orderNo();
        Orders order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())).isEmpty();

        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                eq(orderNo),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
        )).thenThrow(new TossPaymentException(
                HttpStatus.GATEWAY_TIMEOUT,
                "TOSS_READ_TIMEOUT",
                "토스 결제는 처리됐지만 응답 시간이 초과되었습니다."
        ));
        when(tossBillingClient.getPaymentByOrderId(orderNo))
                .thenReturn(successResponse(orderNo, PLUS_RENEWAL_AMOUNT, "Plus Subscription"));

        LocalDateTime beforeReconciliation = LocalDateTime.now();
        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(orderNo)
        );

        verify(tossBillingClient).getPaymentByOrderId(orderNo);
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow())
                .satisfies(payment -> {
                    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
                    assertThat(payment.getTotalAmount()).isEqualTo(PLUS_RENEWAL_AMOUNT);
                    assertThat(payment.getPaymentKey()).isEqualTo("payment-key-" + orderNo);
                });

        Subscriptions reconciledSubscription = subscriptionsRepository
                .findByWorkspaceId(WORKSPACE_ID)
                .orElseThrow();
        assertThat(reconciledSubscription.getSubscriptionPlanId()).isEqualTo(PLUS_PLAN_ID);
        assertThat(reconciledSubscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(reconciledSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reconciledSubscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforeReconciliation);
        assertThat(reconciledSubscription.getCurrentPeriodEnd())
                .isEqualTo(reconciledSubscription.getCurrentPeriodStart().plusDays(30));
    }

    @Test
    @DisplayName("Pro 플랜 구독 기간 만료 후 자동결제로 구독이 갱신된다")
    void expiredProSubscription_thenAutomaticallyPaidAndRenewed() {
        Billing billing = defaultBilling();
        LocalDateTime previousPeriodStart = LocalDateTime.now().minusDays(31);
        LocalDateTime previousPeriodEnd = LocalDateTime.now().minusDays(1);
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PRO_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(previousPeriodStart)
                .currentPeriodEnd(previousPeriodEnd)
                .billingId(billing.getId())
                .build());

        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                anyString(),
                eq(PRO_RENEWAL_AMOUNT),
                eq("Pro Subscription"),
                anyString()
        )).thenAnswer(invocation -> successResponse(
                invocation.getArgument(2),
                PRO_RENEWAL_AMOUNT
        ));

        LocalDateTime beforeRenewal = LocalDateTime.now();
        subscriptionRenewalService.renewDueSubscriptions();

        Orders renewalOrder = orderRepository
                .findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID)
                .getFirst();
        assertThat(renewalOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(renewalOrder.getOrderType()).isEqualTo(OrderType.Billing);
        assertThat(renewalOrder.getTotalAmount()).isEqualTo(PRO_RENEWAL_AMOUNT);
        assertThat(renewalOrder.getSubscriptionProductId()).isEqualTo(PRO_PRODUCT_ID);

        Payments payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(renewalOrder.getId())
                .orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getTotalAmount()).isEqualTo(PRO_RENEWAL_AMOUNT);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-" + renewalOrder.getOrderNo());

        Subscriptions renewedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(renewedSubscription.getSubscriptionPlanId()).isEqualTo(PRO_PLAN_ID);
        assertThat(renewedSubscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(renewedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(renewedSubscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforeRenewal);
        assertThat(renewedSubscription.getCurrentPeriodEnd())
                .isEqualTo(renewedSubscription.getCurrentPeriodStart().plusDays(30));
        assertThat(renewedSubscription.getCurrentPeriodEnd()).isAfter(previousPeriodEnd);

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(PRO_PLAN_ID);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(period.getPeriodStart()).isEqualTo(renewedSubscription.getCurrentPeriodStart());
                    assertThat(period.getPeriodEnd()).isEqualTo(renewedSubscription.getCurrentPeriodEnd());
                });
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    @Test
    @DisplayName("Pro 플랜 만료 시 예약된 Plus 플랜이 자동결제되고 구독이 변경된다")
    void expiredProSubscriptionWithScheduledPlus_thenPlusIsPaidAndApplied() {
        Billing billing = defaultBilling();
        LocalDateTime previousPeriodStart = LocalDateTime.now().minusDays(31);
        LocalDateTime previousPeriodEnd = LocalDateTime.now().minusDays(1);
        Subscriptions subscription = subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PRO_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(previousPeriodStart)
                .currentPeriodEnd(previousPeriodEnd)
                .billingId(billing.getId())
                .build());
        SubscriptionScheduledChanges scheduledChange = subscriptionScheduledChangesRepository.saveAndFlush(
                SubscriptionScheduledChanges.builder()
                        .memberId(MEMBER_ID)
                        .subscriptionId(subscription.getId())
                        .requestedPlanId(PLUS_PLAN_ID)
                        .changeType(SubscriptionChangeType.PLAN_CHANGE)
                        .changeStatus(SubscriptionChangeStatus.SCHEDULED)
                        .requestedAt(previousPeriodStart.plusDays(10))
                        .scheduledAt(previousPeriodEnd)
                        .build()
        );

        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                anyString(),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
        )).thenAnswer(invocation -> successResponse(
                invocation.getArgument(2),
                PLUS_RENEWAL_AMOUNT,
                "Plus Subscription"
        ));

        subscriptionExpirationService.expirePaidSubscriptions();
        assertThat(subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow().getSubscriptionPlanId())
                .isEqualTo(PRO_PLAN_ID);

        LocalDateTime beforeRenewal = LocalDateTime.now();
        subscriptionRenewalService.renewDueSubscriptions();

        Orders renewalOrder = orderRepository
                .findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID)
                .getFirst();
        assertThat(renewalOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(renewalOrder.getTotalAmount()).isEqualTo(PLUS_RENEWAL_AMOUNT);
        assertThat(renewalOrder.getSubscriptionProductId()).isEqualTo(PLUS_PRODUCT_ID);

        Payments payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(renewalOrder.getId())
                .orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getTotalAmount()).isEqualTo(PLUS_RENEWAL_AMOUNT);

        Subscriptions changedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(changedSubscription.getSubscriptionPlanId()).isEqualTo(PLUS_PLAN_ID);
        assertThat(changedSubscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(changedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(changedSubscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforeRenewal);
        assertThat(changedSubscription.getCurrentPeriodEnd())
                .isEqualTo(changedSubscription.getCurrentPeriodStart().plusDays(30));

        assertThat(subscriptionScheduledChangesRepository.findById(scheduledChange.getId()).orElseThrow()
                .getChangeStatus()).isEqualTo(SubscriptionChangeStatus.APPLIED);
        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .extracting(SubscriptionPeriods::getPlanId)
                .isEqualTo(PLUS_PLAN_ID);
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    @Test
    @DisplayName("예약된 유료 플랜 갱신 결제가 확정 실패하면 예약이 취소된다")
    void scheduledPaidPlanRenewalDefinitiveFailure_thenScheduledChangeIsCanceled() {
        Billing billing = defaultBilling();
        ScheduledPlanChangeFixture fixture = saveExpiredProSubscriptionWithScheduledPlus(billing);
        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                anyString(),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
        )).thenThrow(new TossPaymentException(
                HttpStatus.BAD_REQUEST,
                "REJECT_CARD_PAYMENT",
                "카드 결제 거절"
        ));

        subscriptionRenewalService.renewDueSubscriptions();

        Orders failedOrder = orderRepository
                .findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID)
                .getFirst();
        assertThat(failedOrder.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(subscriptionsRepository.findById(fixture.subscription().getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscriptionScheduledChangesRepository.findById(fixture.scheduledChange().getId()).orElseThrow())
                .satisfies(scheduledChange -> {
                    assertThat(scheduledChange.getChangeStatus()).isEqualTo(SubscriptionChangeStatus.CANCELED);
                    assertThat(scheduledChange.getCanceledAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("예약된 유료 플랜 갱신 결제가 일시 실패하면 재시도를 위해 예약을 유지한다")
    void scheduledPaidPlanRenewalTemporaryFailure_thenScheduledChangeIsKept() {
        Billing billing = defaultBilling();
        ScheduledPlanChangeFixture fixture = saveExpiredProSubscriptionWithScheduledPlus(billing);
        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                anyString(),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
        )).thenThrow(new TossPaymentException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "FAILED_INTERNAL_SYSTEM_PROCESSING",
                "토스 일시 장애"
        ));

        subscriptionRenewalService.renewDueSubscriptions();

        Orders retryScheduledOrder = orderRepository
                .findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID)
                .getFirst();
        assertThat(retryScheduledOrder.getOrderStatus()).isEqualTo(OrderStatus.RETRY_SCHEDULED);
        assertThat(subscriptionsRepository.findById(fixture.subscription().getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscriptionScheduledChangesRepository.findById(fixture.scheduledChange().getId()).orElseThrow())
                .satisfies(scheduledChange -> {
                    assertThat(scheduledChange.getChangeStatus()).isEqualTo(SubscriptionChangeStatus.SCHEDULED);
                    assertThat(scheduledChange.getCanceledAt()).isNull();
                });
    }

    @Test
    @DisplayName("STANDARD 구독 기간 만료 시 결제 없이 STANDARD 플랜으로 갱신된다")
    void expiredStandardSubscription_thenRenewsWithoutPayment() {
        LocalDateTime previousPeriodStart = LocalDateTime.now().minusDays(366);
        LocalDateTime previousPeriodEnd = LocalDateTime.now().minusDays(1);
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(STANDARD_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(previousPeriodStart)
                .currentPeriodEnd(previousPeriodEnd)
                .build());

        LocalDateTime beforeRenewal = LocalDateTime.now();
        subscriptionRenewalService.renewDueSubscriptions();

        Subscriptions renewedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(renewedSubscription.getSubscriptionPlanId()).isEqualTo(STANDARD_PLAN_ID);
        assertThat(renewedSubscription.getBillingId()).isNull();
        assertThat(renewedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(renewedSubscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforeRenewal);
        assertThat(renewedSubscription.getCurrentPeriodEnd())
                .isEqualTo(renewedSubscription.getCurrentPeriodStart().plusDays(365));

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(STANDARD_PLAN_ID);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(period.getPeriodStart()).isEqualTo(renewedSubscription.getCurrentPeriodStart());
                    assertThat(period.getPeriodEnd()).isEqualTo(renewedSubscription.getCurrentPeriodEnd());
                });
        assertThat(orderRepository.findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID))
                .isEmpty();
        assertThat(paymentRepository.findAll()).isEmpty();
        verifyNoInteractions(tossBillingClient);
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    @ParameterizedTest(name = "현재 플랜 ID {0}에서 STANDARD 예약 변경")
    @ValueSource(longs = {2L, 3L})
    @DisplayName("Plus 또는 Pro 플랜 만료 시 예약된 STANDARD 플랜으로 결제 없이 변경된다")
    void expiredPaidSubscriptionWithScheduledStandard_thenStandardIsAppliedWithoutPayment(long currentPlanId) {
        Billing billing = defaultBilling();
        LocalDateTime previousPeriodStart = LocalDateTime.now().minusDays(31);
        LocalDateTime previousPeriodEnd = LocalDateTime.now().minusDays(1);
        Subscriptions subscription = subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(currentPlanId)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(previousPeriodStart)
                .currentPeriodEnd(previousPeriodEnd)
                .billingId(billing.getId())
                .build());
        SubscriptionScheduledChanges scheduledChange = subscriptionScheduledChangesRepository.saveAndFlush(
                SubscriptionScheduledChanges.builder()
                        .memberId(MEMBER_ID)
                        .subscriptionId(subscription.getId())
                        .requestedPlanId(STANDARD_PLAN_ID)
                        .changeType(SubscriptionChangeType.PLAN_CHANGE)
                        .changeStatus(SubscriptionChangeStatus.SCHEDULED)
                        .requestedAt(previousPeriodStart.plusDays(10))
                        .scheduledAt(previousPeriodEnd)
                        .build()
        );

        LocalDateTime beforeChange = LocalDateTime.now();
        subscriptionExpirationService.expirePaidSubscriptions();

        Subscriptions changedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(changedSubscription.getSubscriptionPlanId()).isEqualTo(STANDARD_PLAN_ID);
        assertThat(changedSubscription.getBillingId()).isNull();
        assertThat(changedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(changedSubscription.getCurrentPeriodStart()).isAfterOrEqualTo(beforeChange);
        assertThat(changedSubscription.getCurrentPeriodEnd())
                .isEqualTo(changedSubscription.getCurrentPeriodStart().plusDays(365));

        assertThat(subscriptionScheduledChangesRepository.findById(scheduledChange.getId()).orElseThrow()
                .getChangeStatus()).isEqualTo(SubscriptionChangeStatus.APPLIED);
        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(STANDARD_PLAN_ID);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(period.getPeriodStart()).isEqualTo(changedSubscription.getCurrentPeriodStart());
                    assertThat(period.getPeriodEnd()).isEqualTo(changedSubscription.getCurrentPeriodEnd());
                });

        assertThat(orderRepository.findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID))
                .isEmpty();
        assertThat(paymentRepository.findAll()).isEmpty();
        verifyNoInteractions(tossBillingClient);
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    private Billing defaultBilling() {
        return billingRepository.findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                MEMBER_ID,
                com.haagendazs.payment.payment.enums.BillingStatus.ACTIVE
        ).orElseThrow();
    }

    private ScheduledPlanChangeFixture saveExpiredProSubscriptionWithScheduledPlus(Billing billing) {
        LocalDateTime previousPeriodStart = LocalDateTime.now().minusDays(31);
        LocalDateTime previousPeriodEnd = LocalDateTime.now().minusDays(1);
        Subscriptions subscription = subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PRO_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(previousPeriodStart)
                .currentPeriodEnd(previousPeriodEnd)
                .billingId(billing.getId())
                .build());
        SubscriptionScheduledChanges scheduledChange = subscriptionScheduledChangesRepository.saveAndFlush(
                SubscriptionScheduledChanges.builder()
                        .memberId(MEMBER_ID)
                        .subscriptionId(subscription.getId())
                        .requestedPlanId(PLUS_PLAN_ID)
                        .changeType(SubscriptionChangeType.PLAN_CHANGE)
                        .changeStatus(SubscriptionChangeStatus.SCHEDULED)
                        .requestedAt(previousPeriodStart.plusDays(10))
                        .scheduledAt(previousPeriodEnd)
                        .build()
        );

        return new ScheduledPlanChangeFixture(subscription, scheduledChange);
    }

    private record ScheduledPlanChangeFixture(
            Subscriptions subscription,
            SubscriptionScheduledChanges scheduledChange
    ) {
    }

    private TossBillingPaymentResponse successResponse(String orderNo) {
        return successResponse(orderNo, UPGRADE_AMOUNT);
    }

    private TossBillingPaymentResponse successResponse(String orderNo, Long amount) {
        return successResponse(orderNo, amount, "Pro Subscription");
    }

    private TossBillingPaymentResponse successResponse(String orderNo, Long amount, String orderName) {
        return new TossBillingPaymentResponse(
                "payment-key-" + orderNo,
                orderNo,
                orderName,
                "DONE",
                amount,
                "2026-07-20T10:00:00+09:00",
                "2026-07-20T10:00:01+09:00",
                new TossBillingPaymentResponse.TossCardInfo(
                        "11",
                        "11",
                        "47034911****333*",
                        "신용",
                        "개인"
                ),
                new TossBillingPaymentResponse.TossReceipt("https://receipt.example")
        );
    }
}
