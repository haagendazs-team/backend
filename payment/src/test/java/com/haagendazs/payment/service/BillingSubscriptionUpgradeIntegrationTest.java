package com.haagendazs.payment.service;

import com.haagendazs.TestPaymentApplication;
import com.haagendazs.common.exception.GlobalExceptionHandler;
import com.haagendazs.presentation.controller.PaymentController;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.application.service.SubscriptionExpirationService;
import com.haagendazs.infrastructure.kafka.PaymentEventProducer;
import com.haagendazs.infrastructure.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.domain.model.Orders;
import com.haagendazs.domain.model.OrderStatus;
import com.haagendazs.domain.model.OrderType;
import com.haagendazs.domain.repository.OrderRepository;
import com.haagendazs.domain.model.Billing;
import com.haagendazs.domain.model.Payments;
import com.haagendazs.domain.model.BillingStatus;
import com.haagendazs.domain.model.PaymentStatus;
import com.haagendazs.domain.model.TossPaymentErrorCode;
import com.haagendazs.domain.repository.BillingRepository;
import com.haagendazs.domain.repository.PaymentRepository;
import com.haagendazs.domain.repository.PaymentRetryJobRepository;
import com.haagendazs.application.service.BillingPaymentService;
import com.haagendazs.application.dto.BillingPaymentRequest;
import com.haagendazs.application.dto.TossBillingPaymentResponse;
import com.haagendazs.infrastructure.toss.TossBillingClient;
import com.haagendazs.infrastructure.toss.TossPaymentException;
import com.haagendazs.domain.model.SubscriptionPeriods;
import com.haagendazs.domain.model.SubscriptionScheduledChanges;
import com.haagendazs.domain.model.Subscriptions;
import com.haagendazs.domain.model.SubscriptionChangeAction;
import com.haagendazs.domain.model.SubscriptionChangeStatus;
import com.haagendazs.domain.model.SubscriptionChangeType;
import com.haagendazs.domain.model.SubscriptionStatus;
import com.haagendazs.domain.repository.SubscriptionPeriodsRepository;
import com.haagendazs.domain.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.domain.repository.SubscriptionsRepository;
import com.haagendazs.application.service.SubscriptionRenewalService;
import com.haagendazs.application.service.SubscriptionService;
import com.haagendazs.application.dto.ChangeSubscriptionRequest;
import com.haagendazs.application.dto.ChangeSubscriptionResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private PaymentController paymentController;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentRetryJobRepository paymentRetryJobRepository;

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
    @DisplayName("빌링키 발급 실패 시 사용자 오류로 변환하고 Toss 원본 오류는 내부에 보존한다")
    void billingKeyIssueFailure_thenReturnsPaymentErrorAndPreservesTossError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        long billingCountBeforeIssue = billingRepository.count();
        TossPaymentException tossException = new TossPaymentException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CARD_PASSWORD",
                "카드 비밀번호가 일치하지 않습니다."
        );
        when(tossBillingClient.issueBillingKey("invalid-auth-key", "customer-key-seed-1"))
                .thenThrow(tossException);

        MvcResult result = mockMvc.perform(post("/api/v1/payments/billing-methods/confirm")
                        .header("X-Member-Id", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authKey\":\"invalid-auth-key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("P022"))
                .andExpect(jsonPath("$.message").value("카드정보가 잘못되었습니다."))
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOfSatisfying(TossPaymentException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_CARD_INFO);
                    assertThat(exception.getTossPaymentErrorCode())
                            .isEqualTo(TossPaymentErrorCode.INVALID_CARD_PASSWORD);
                    assertThat(exception.getTossCode()).isEqualTo("INVALID_CARD_PASSWORD");
                    assertThat(exception.getTossMessage()).isEqualTo("카드 비밀번호가 일치하지 않습니다.");
                });
        assertThat(billingRepository.count()).isEqualTo(billingCountBeforeIssue);
    }

    @ParameterizedTest(name = "빌링키 상태가 {0}이면 자동결제를 시작하지 않는다")
    @EnumSource(value = BillingStatus.class, names = {"INACTIVE", "EXPIRED"})
    @DisplayName("빌링키가 ACTIVE 상태가 아니면 결제 시퀀스를 종료하고 상태 변경을 남기지 않는다")
    void nonActiveBillingMethod_thenStopsPaymentAndKeepsPreviousState(BillingStatus billingStatus) {
        Billing billing = defaultBilling();
        LocalDateTime periodStart = LocalDateTime.now().minusDays(30);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(335);
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(STANDARD_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .build());
        ChangeSubscriptionResponse changeResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PLUS_PRODUCT_ID)
        );
        Orders orderBeforePayment = orderRepository.findByOrderNo(changeResponse.orderNo()).orElseThrow();

        if (billingStatus == BillingStatus.EXPIRED) {
            billing.expire();
        } else {
            billing.deactivate();
        }
        billingRepository.flush();

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(
                MEMBER_ID,
                changeResponse.orderNo(),
                billing
        )).extracting("errorCode").isEqualTo(PaymentErrorCode.BILLING_METHOD_NOT_FOUND);

        assertThat(orderRepository.findByOrderNo(changeResponse.orderNo()).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderBeforePayment.getId())).isEmpty();
        assertThat(subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow())
                .satisfies(subscription -> {
                    assertThat(subscription.getSubscriptionPlanId()).isEqualTo(STANDARD_PLAN_ID);
                    assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(subscription.getCurrentPeriodStart()).isEqualTo(periodStart);
                    assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
                });
        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID)).isEmpty();
        verifyNoInteractions(tossBillingClient);
    }

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
        assertThat(changeResponse.action()).isEqualTo(SubscriptionChangeAction.PAYMENT_REQUIRED);
        assertThat(changeResponse.amount()).isEqualTo(PLUS_RENEWAL_AMOUNT);

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

        verify(tossBillingClient).payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                eq(orderNo),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
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

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .satisfies(period -> {
                    assertThat(period.getPlanId()).isEqualTo(PLUS_PLAN_ID);
                    assertThat(period.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(period.getPeriodStart()).isEqualTo(reconciledSubscription.getCurrentPeriodStart());
                    assertThat(period.getPeriodEnd()).isEqualTo(reconciledSubscription.getCurrentPeriodEnd());
                });
        verify(paymentEventProducer).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
    }

    @Test
    @DisplayName("결제 응답 시간이 초과되면 Toss 결제 상태를 확인해 미결제 주문을 실패로 보정한다")
    void billingPaymentReadTimeoutAndTossPaymentAborted_thenReconcilesOrderAsFailed() {
        Billing billing = defaultBilling();
        LocalDateTime periodStart = LocalDateTime.now().minusDays(30);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(335);
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(STANDARD_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .build());

        ChangeSubscriptionResponse changeResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PLUS_PRODUCT_ID)
        );
        String orderNo = changeResponse.orderNo();
        Orders order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        TossPaymentException timeoutException = new TossPaymentException(
                HttpStatus.GATEWAY_TIMEOUT,
                "TOSS_READ_TIMEOUT",
                "토스 결제 처리 여부를 확인할 수 없어 응답 시간이 초과되었습니다."
        );
        TossBillingPaymentResponse abortedPayment = new TossBillingPaymentResponse(
                null,
                orderNo,
                "Plus Subscription",
                "ABORTED",
                PLUS_RENEWAL_AMOUNT,
                "2026-07-20T10:00:00+09:00",
                null,
                null,
                null
        );
        when(tossBillingClient.payWithBillingKey(
                eq(billing.getBillingKey()),
                eq("customer-key-seed-1"),
                eq(orderNo),
                eq(PLUS_RENEWAL_AMOUNT),
                eq("Plus Subscription"),
                anyString()
        )).thenThrow(timeoutException);
        when(tossBillingClient.getPaymentByOrderId(orderNo)).thenReturn(abortedPayment);

        assertThatThrownBy(() -> billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(orderNo)
        )).isSameAs(timeoutException);

        verify(tossBillingClient).getPaymentByOrderId(orderNo);
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.FAILED);
        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())).isEmpty();

        assertThat(subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow())
                .satisfies(subscription -> {
                    assertThat(subscription.getSubscriptionPlanId()).isEqualTo(STANDARD_PLAN_ID);
                    assertThat(subscription.getBillingId()).isNull();
                    assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(subscription.getCurrentPeriodStart()).isEqualTo(periodStart);
                    assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
                });
        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID)).isEmpty();
        verify(paymentEventProducer).publishPaymentFailed(eq(MEMBER_ID), any());
        verify(paymentEventProducer, never()).publishSubscriptionChanged(any(SubscriptionChangedEvent.class));
        verify(paymentEventProducer, never()).publishWorkspaceSubscribed(any(WorkspaceSubscribedEvent.class));
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
    @DisplayName("자동결제 시 카드 유효기간이 만료되면 재결제를 예약하지 않고 빌링키를 만료 처리한다")
    void subscriptionRenewalInvalidCardExpiration_thenExpiresBillingWithoutRetry() {
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
                "INVALID_CARD_EXPIRATION",
                "카드 유효기간 정보를 다시 확인해주세요."
        ));

        subscriptionRenewalService.renewDueSubscriptions();

        Orders failedOrder = orderRepository
                .findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID)
                .getFirst();
        assertThat(failedOrder.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(paymentRetryJobRepository.findAll()).isEmpty();
        assertThat(billingRepository.findById(billing.getId()).orElseThrow())
                .satisfies(expiredBilling -> {
                    assertThat(expiredBilling.getBillingStatus()).isEqualTo(BillingStatus.EXPIRED);
                    assertThat(expiredBilling.getIsDefault()).isFalse();
                    assertThat(expiredBilling.getBillingKey()).isEqualTo(billing.getBillingKey());
                });
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
                com.haagendazs.domain.model.BillingStatus.ACTIVE
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
