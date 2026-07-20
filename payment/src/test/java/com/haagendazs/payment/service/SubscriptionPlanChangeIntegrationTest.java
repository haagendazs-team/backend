package com.haagendazs.payment.service;

import com.haagendazs.payment.TestPaymentApplication;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeAction;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import com.haagendazs.payment.subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.subscription.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionRequest;
import com.haagendazs.payment.subscription.service.dto.ChangeSubscriptionResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = TestPaymentApplication.class)
@Transactional
@ActiveProfiles("test")
// Run: test 또는 ./gradlew :payment:test --tests com.haagendazs.payment.service.SubscriptionPlanChangeIntegrationTest
class SubscriptionPlanChangeIntegrationTest {

    private static final Long MEMBER_ID = 7101L;
    private static final Long WORKSPACE_ID = 7101L;
    private static final Long PRO_PLAN_ID = 3L;
    private static final Long PLUS_PLAN_ID = 2L;
    private static final Long PLUS_PRODUCT_ID = 2L;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionsRepository subscriptionsRepository;

    @Autowired
    private SubscriptionScheduledChangesRepository subscriptionScheduledChangesRepository;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    @Test
    @DisplayName("높은 플랜에서 낮은 플랜으로 변경하면 다음 기간 변경 예약이 생성된다")
    void changeToLowerPlan_thenScheduledPlanChangeIsCreated() {
        LocalDateTime periodStart = LocalDateTime.now().minusDays(20);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(10);
        Subscriptions subscription = subscriptionsRepository.saveAndFlush(Subscriptions.builder()
                .subscriptionPlanId(PRO_PLAN_ID)
                .workspaceId(WORKSPACE_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .build());

        ChangeSubscriptionResponse response = subscriptionService.changeSubscription(
                MEMBER_ID,
                WORKSPACE_ID,
                new ChangeSubscriptionRequest(PLUS_PRODUCT_ID)
        );

        assertThat(response.action()).isEqualTo(SubscriptionChangeAction.SCHEDULED);
        assertThat(response.orderId()).isNull();
        assertThat(response.orderNo()).isNull();
        assertThat(response.amount()).isNull();

        Subscriptions unchangedSubscription = subscriptionsRepository.findByWorkspaceId(WORKSPACE_ID).orElseThrow();
        assertThat(unchangedSubscription.getSubscriptionPlanId()).isEqualTo(PRO_PLAN_ID);
        assertThat(unchangedSubscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);

        List<SubscriptionScheduledChanges> scheduledChanges =
                subscriptionScheduledChangesRepository.findBySubscriptionIdAndMemberIdAndChangeTypeAndChangeStatus(
                        subscription.getId(),
                        MEMBER_ID,
                        SubscriptionChangeType.PLAN_CHANGE,
                        SubscriptionChangeStatus.SCHEDULED
                );
        assertThat(scheduledChanges)
                .singleElement()
                .satisfies(scheduledChange -> {
                    assertThat(scheduledChange.getRequestedPlanId()).isEqualTo(PLUS_PLAN_ID);
                    assertThat(scheduledChange.getRequestedAt()).isNotNull();
                    assertThat(scheduledChange.getScheduledAt()).isEqualTo(periodEnd);
                    assertThat(scheduledChange.getCanceledAt()).isNull();
                });

        verify(orderService, never()).createSubscriptionOrderWithAmount(any(), any(), any(), any());
    }
}
