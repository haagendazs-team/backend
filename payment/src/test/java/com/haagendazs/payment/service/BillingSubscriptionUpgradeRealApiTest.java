package com.haagendazs.payment.service;

import com.haagendazs.TestPaymentApplication;
import com.haagendazs.application.service.SubscriptionExpirationService;
import com.haagendazs.infrastructure.kafka.PaymentEventProducer;
import com.haagendazs.domain.model.Orders;
import com.haagendazs.domain.model.OrderStatus;
import com.haagendazs.domain.model.OrderType;
import com.haagendazs.domain.repository.OrderRepository;
import com.haagendazs.application.service.OrderService;
import com.haagendazs.application.dto.OrderCreateResponse;
import com.haagendazs.domain.model.Billing;
import com.haagendazs.domain.model.PaymentRetryJob;
import com.haagendazs.domain.model.Payments;
import com.haagendazs.domain.model.BillingStatus;
import com.haagendazs.domain.model.PaymentRetryJobStatus;
import com.haagendazs.domain.model.PaymentStatus;
import com.haagendazs.domain.repository.BillingRepository;
import com.haagendazs.domain.repository.PaymentRepository;
import com.haagendazs.domain.repository.PaymentRetryJobRepository;
import com.haagendazs.application.service.BillingPaymentService;
import com.haagendazs.application.service.PaymentRetryJobService;
import com.haagendazs.application.service.PaymentRetryJobTransactionService;
import com.haagendazs.application.dto.BillingPaymentRequest;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestPaymentApplication.class)
@ActiveProfiles("test")
@Transactional
@Tag("external")
@Tag("service-real-api")
// Run: ./gradlew :payment:tossServiceRealApiTest --tests com.haagendazs.payment.service.BillingSubscriptionUpgradeRealApiTest
class BillingSubscriptionUpgradeRealApiTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long WORKSPACE_ID = 7201L;
    private static final Long STANDARD_PLAN_ID = 1L;
    private static final Long PLUS_PLAN_ID = 2L;
    private static final Long PLUS_PRODUCT_ID = 2L;
    private static final Long PLUS_PRICE = 19900L;
    private static final Long PRO_PLAN_ID = 3L;
    private static final Long PRO_PRODUCT_ID = 3L;
    private static final Long PRO_PRICE = 29900L;
    private static final Long PLUS_TO_PRO_UPGRADE_PRICE = PRO_PRICE - PLUS_PRICE;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRenewalService subscriptionRenewalService;

    @Autowired
    private SubscriptionExpirationService subscriptionExpirationService;

    @Autowired
    private BillingPaymentService billingPaymentService;

    @Autowired
    private PaymentRetryJobService paymentRetryJobService;

    @Autowired
    private PaymentRetryJobTransactionService paymentRetryJobTransactionService;

    @Autowired
    private OrderService orderService;

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
    private SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;

    // 이 테스트의 외부 연동 대상은 Toss이며 Kafka는 범위에서 제외합니다.
    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    @Test
    @DisplayName("실제 Toss 서버 - STANDARD에서 Plus 업그레이드 결제 후 구독이 변경된다")
    void standardSubscriptionUpgradeToPlus_usesRealTossServer() {
        Billing billing = billingRepository
                .findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                        MEMBER_ID,
                        BillingStatus.ACTIVE
                )
                .orElseThrow();
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
        assertThat(changeResponse.amount()).isEqualTo(PLUS_PRICE);
        assertThat(changeResponse.orderType()).isEqualTo(OrderType.Billing);

        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(changeResponse.orderNo())
        );

        Orders paidOrder = orderRepository.findByOrderNo(changeResponse.orderNo()).orElseThrow();
        assertThat(paidOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getTotalAmount()).isEqualTo(PLUS_PRICE);
        assertThat(paidOrder.getSubscriptionProductId()).isEqualTo(PLUS_PRODUCT_ID);

        Payments payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(paidOrder.getId())
                .orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getTotalAmount()).isEqualTo(PLUS_PRICE);
        assertThat(payment.getPaymentKey()).isNotBlank();
        assertThat(payment.getReceiptUrl()).isNotBlank();

        Subscriptions upgradedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(upgradedSubscription.getSubscriptionPlanId()).isEqualTo(PLUS_PLAN_ID);
        assertThat(upgradedSubscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(upgradedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(upgradedSubscription.getCurrentPeriodEnd())
                .isEqualTo(upgradedSubscription.getCurrentPeriodStart().plusDays(30));

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .extracting(SubscriptionPeriods::getPlanId)
                .isEqualTo(PLUS_PLAN_ID);
    }

    @Test
    @DisplayName("실제 Toss 서버 - STANDARD에서 Pro 업그레이드 시 29,900원을 결제한다")
    void standardSubscriptionUpgradeToPro_usesRealTossServer() {
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
                new ChangeSubscriptionRequest(PRO_PRODUCT_ID)
        );
        assertThat(changeResponse.action()).isEqualTo(SubscriptionChangeAction.PAYMENT_REQUIRED);
        assertThat(changeResponse.amount()).isEqualTo(PRO_PRICE);

        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(changeResponse.orderNo())
        );

        assertPaidSubscriptionChange(
                changeResponse.orderNo(),
                PRO_PRODUCT_ID,
                PRO_PRICE,
                PRO_PLAN_ID,
                billing
        );
    }

    @Test
    @DisplayName("실제 Toss 서버 - Plus에서 Pro 업그레이드 시 10,000원 차액을 결제한다")
    void plusSubscriptionUpgradeToPro_usesRealTossServer() {
        Billing billing = defaultBilling();
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PLUS_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(10))
                .currentPeriodEnd(LocalDateTime.now().plusDays(20))
                .billingId(billing.getId())
                .build());

        ChangeSubscriptionResponse changeResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PRO_PRODUCT_ID)
        );
        assertThat(changeResponse.action()).isEqualTo(SubscriptionChangeAction.PAYMENT_REQUIRED);
        assertThat(changeResponse.amount()).isEqualTo(PLUS_TO_PRO_UPGRADE_PRICE);

        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(changeResponse.orderNo())
        );

        assertPaidSubscriptionChange(
                changeResponse.orderNo(),
                PRO_PRODUCT_ID,
                PLUS_TO_PRO_UPGRADE_PRICE,
                PRO_PLAN_ID,
                billing
        );
    }

    @Test
    @DisplayName("실제 Toss 서버 - Pro 구독 만료 후 29,900원 자동결제로 갱신된다")
    void expiredProSubscriptionRenewal_usesRealTossServer() {
        Billing billing = defaultBilling();
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PRO_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(31))
                .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                .billingId(billing.getId())
                .build());

        subscriptionRenewalService.renewDueSubscriptions();

        Orders paidOrder = latestOrder();
        assertPaidSubscriptionChange(
                paidOrder.getOrderNo(),
                PRO_PRODUCT_ID,
                PRO_PRICE,
                PRO_PLAN_ID,
                billing
        );
    }

    @Test
    @DisplayName("실제 Toss 서버 - Plus 구독 만료 후 19,900원 자동결제로 갱신된다")
    void expiredPlusSubscriptionRenewal_usesRealTossServer() {
        Billing billing = defaultBilling();
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PLUS_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(31))
                .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                .billingId(billing.getId())
                .build());

        subscriptionRenewalService.renewDueSubscriptions();

        Orders paidOrder = latestOrder();
        assertPaidSubscriptionChange(
                paidOrder.getOrderNo(),
                PLUS_PRODUCT_ID,
                PLUS_PRICE,
                PLUS_PLAN_ID,
                billing
        );
    }

    @Test
    @DisplayName("실제 Toss 서버 - Pro 만료 시 예약된 Plus를 19,900원 결제하고 적용한다")
    void expiredProSubscriptionWithScheduledPlus_usesRealTossServer() {
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

        subscriptionExpirationService.expirePaidSubscriptions();
        subscriptionRenewalService.renewDueSubscriptions();

        Orders paidOrder = latestOrder();
        assertPaidSubscriptionChange(
                paidOrder.getOrderNo(),
                PLUS_PRODUCT_ID,
                PLUS_PRICE,
                PLUS_PLAN_ID,
                billing
        );
        assertThat(subscriptionScheduledChangesRepository.findById(scheduledChange.getId()).orElseThrow()
                .getChangeStatus()).isEqualTo(SubscriptionChangeStatus.APPLIED);
    }

    @Test
    @DisplayName("실제 Toss 서버 - Pro에서 Plus 변경 예약을 취소하면 만료 후 Pro로 자동 갱신된다")
    void canceledScheduledPlus_thenExpiredProRenewsWithRealTossServer() {
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

        ChangeSubscriptionResponse scheduleResponse = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PLUS_PRODUCT_ID)
        );
        assertThat(scheduleResponse.action()).isEqualTo(SubscriptionChangeAction.SCHEDULED);
        SubscriptionScheduledChanges scheduledChange = subscriptionScheduledChangesRepository
                .findBySubscriptionIdAndMemberIdAndChangeTypeAndChangeStatus(
                        subscription.getId(),
                        MEMBER_ID,
                        SubscriptionChangeType.PLAN_CHANGE,
                        SubscriptionChangeStatus.SCHEDULED
                )
                .getFirst();

        subscriptionService.cancelScheduledPlanChange(MEMBER_ID, WORKSPACE_ID);
        assertThat(scheduledChange.getChangeStatus()).isEqualTo(SubscriptionChangeStatus.CANCELED);
        assertThat(scheduledChange.getCanceledAt()).isNotNull();

        subscriptionRenewalService.renewDueSubscriptions();

        Orders paidOrder = latestOrder();
        assertPaidSubscriptionChange(
                paidOrder.getOrderNo(),
                PRO_PRODUCT_ID,
                PRO_PRICE,
                PRO_PLAN_ID,
                billing
        );
        assertThat(subscriptionScheduledChangesRepository.findById(scheduledChange.getId()).orElseThrow()
                .getChangeStatus()).isEqualTo(SubscriptionChangeStatus.CANCELED);
    }

    @Test
    @DisplayName("실제 Toss 서버 - 재시도 등록된 Plus 구독 결제를 수행하고 갱신한다")
    void scheduledPlusRenewalRetry_usesRealTossServer() {
        Billing billing = defaultBilling();
        subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PLUS_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(31))
                .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                .billingId(billing.getId())
                .build());
        OrderCreateResponse renewalOrder = orderService.createSubscriptionOrderWithAmount(
                MEMBER_ID,
                WORKSPACE_ID,
                PLUS_PRODUCT_ID,
                PLUS_PRICE
        );
        PaymentRetryJob retryJob = paymentRetryJobTransactionService.scheduleRetry(
                MEMBER_ID,
                renewalOrder.orderNo(),
                billing.getId(),
                3,
                LocalDateTime.now().minusSeconds(1),
                "FAILED_INTERNAL_SYSTEM_PROCESSING",
                "이전 자동결제 일시 실패"
        );
        paymentRetryJobRepository.flush();

        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.SCHEDULED);
        assertThat(orderRepository.findByOrderNo(renewalOrder.orderNo()).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.RETRY_SCHEDULED);
        assertThat(subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PAST_DUE);

        paymentRetryJobService.processDueRetryJobs();

        assertThat(paymentRetryJobRepository.findById(retryJob.getId()).orElseThrow())
                .satisfies(succeededJob -> {
                    assertThat(succeededJob.getStatus()).isEqualTo(PaymentRetryJobStatus.SUCCEEDED);
                    assertThat(succeededJob.getLastTriedAt()).isNotNull();
                    assertThat(succeededJob.getRetryCount()).isZero();
                });
        assertPaidSubscriptionChange(
                renewalOrder.orderNo(),
                PLUS_PRODUCT_ID,
                PLUS_PRICE,
                PLUS_PLAN_ID,
                billing
        );
    }

    private Billing defaultBilling() {
        return billingRepository
                .findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                        MEMBER_ID,
                        BillingStatus.ACTIVE
                )
                .orElseThrow();
    }

    private Orders latestOrder() {
        return orderRepository
                .findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(MEMBER_ID, WORKSPACE_ID)
                .getFirst();
    }

    private void assertPaidSubscriptionChange(
            String orderNo,
            Long productId,
            Long amount,
            Long planId,
            Billing billing
    ) {
        Orders paidOrder = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(paidOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getTotalAmount()).isEqualTo(amount);
        assertThat(paidOrder.getSubscriptionProductId()).isEqualTo(productId);

        Payments payment = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(paidOrder.getId())
                .orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getTotalAmount()).isEqualTo(amount);
        assertThat(payment.getPaymentKey()).isNotBlank();
        assertThat(payment.getReceiptUrl()).isNotBlank();

        Subscriptions subscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(subscription.getSubscriptionPlanId()).isEqualTo(planId);
        assertThat(subscription.getBillingId()).isEqualTo(billing.getId());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(subscription.getCurrentPeriodStart().plusDays(30));

        assertThat(subscriptionPeriodsRepository.findByWorkspaceIdOrderByPeriodStartDesc(WORKSPACE_ID))
                .singleElement()
                .extracting(SubscriptionPeriods::getPlanId)
                .isEqualTo(planId);
    }
}
