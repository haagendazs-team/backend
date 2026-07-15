package com.haagendazs.payment.service;

import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.service.BillingPaymentService;
import com.haagendazs.payment.payment.service.BillingPaymentTransactionService;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import com.haagendazs.payment.payment.service.tools.TossPaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingPaymentServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String ORDER_NO = "ORDER-1";

    @Mock
    private TossBillingClient tossBillingClient;

    @Mock
    private BillingPaymentTransactionService billingPaymentTransactionService;

    @Mock
    private Billing billing;

    private BillingPaymentService billingPaymentService;

    @BeforeEach
    void setUp() {
        billingPaymentService = new BillingPaymentService(
                tossBillingClient,
                billingPaymentTransactionService
        );
    }

    @Test
    @DisplayName("checkout 자동결제 성공 - Toss 성공 응답이면 결제 완료 후처리")
    void payCheckoutWithBillingMethodSuccessTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossBillingPaymentResponse response = successResponse();
        when(billingPaymentTransactionService.prepareBillingPayment(MEMBER_ID, ORDER_NO, billing))
                .thenReturn(preparation);
        when(tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        )).thenReturn(response);

        billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing);

        verify(billingPaymentTransactionService)
                .completeBillingPayment(MEMBER_ID, preparation, response);
        verify(billingPaymentTransactionService, never())
                .failBillingPayment(MEMBER_ID, preparation, null);
    }

    @Test
    @DisplayName("checkout 자동결제 connect timeout - 즉시 실패 처리")
    void payCheckoutWithBillingMethodConnectTimeoutTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossPaymentException exception = tossException("TOSS_CONNECT_TIMEOUT");
        when(billingPaymentTransactionService.prepareBillingPayment(MEMBER_ID, ORDER_NO, billing))
                .thenReturn(preparation);
        when(tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        )).thenThrow(exception);

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);

        verify(billingPaymentTransactionService)
                .failBillingPayment(MEMBER_ID, preparation, exception);
        verify(billingPaymentTransactionService, never())
                .markRetryScheduled(MEMBER_ID, preparation, exception);
    }

    @Test
    @DisplayName("정기 갱신 자동결제 connect timeout - 재시도 예약")
    void paySubscriptionRenewalWithBillingMethodConnectTimeoutTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossPaymentException exception = tossException("TOSS_CONNECT_TIMEOUT");
        when(billingPaymentTransactionService.prepareBillingPayment(MEMBER_ID, ORDER_NO, billing))
                .thenReturn(preparation);
        when(tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        )).thenThrow(exception);

        assertThatThrownBy(() -> billingPaymentService.paySubscriptionRenewalWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);

        verify(billingPaymentTransactionService)
                .markRetryScheduled(MEMBER_ID, preparation, exception);
        verify(billingPaymentTransactionService, never())
                .failBillingPayment(MEMBER_ID, preparation, exception);
    }

    @Test
    @DisplayName("checkout 자동결제 read timeout - 결제 조회 결과 DONE이면 결제 완료 보정")
    void payCheckoutWithBillingMethodReadTimeoutReconcileSuccessTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossPaymentException exception = tossException("TOSS_READ_TIMEOUT");
        TossBillingPaymentResponse queriedResponse = successResponse();
        when(billingPaymentTransactionService.prepareBillingPayment(MEMBER_ID, ORDER_NO, billing))
                .thenReturn(preparation);
        when(tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        )).thenThrow(exception);
        when(tossBillingClient.getPaymentByOrderId(ORDER_NO)).thenReturn(queriedResponse);

        billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing);

        verify(tossBillingClient).getPaymentByOrderId(ORDER_NO);
        verify(billingPaymentTransactionService)
                .completeBillingPayment(MEMBER_ID, preparation, queriedResponse);
        verify(billingPaymentTransactionService, never())
                .failBillingPayment(MEMBER_ID, preparation, exception);
    }

    @Test
    @DisplayName("checkout 자동결제 read timeout - 결제 조회도 실패하면 보정 필요 상태")
    void payCheckoutWithBillingMethodReadTimeoutReconcileFailureTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossPaymentException exception = tossException("TOSS_READ_TIMEOUT");
        RuntimeException reconcileException = new RuntimeException("query failed");
        when(billingPaymentTransactionService.prepareBillingPayment(MEMBER_ID, ORDER_NO, billing))
                .thenReturn(preparation);
        when(tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        )).thenThrow(exception);
        when(tossBillingClient.getPaymentByOrderId(ORDER_NO)).thenThrow(reconcileException);

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);

        verify(billingPaymentTransactionService)
                .markReconcileRequired(MEMBER_ID, preparation, reconcileException);
    }

    private BillingPaymentTransactionService.BillingPaymentPreparation preparation() {
        return new BillingPaymentTransactionService.BillingPaymentPreparation(
                10L,
                ORDER_NO,
                19900L,
                "Plus Subscription",
                20L,
                "billing-key",
                "customer-key",
                "billing-payment-" + ORDER_NO
        );
    }

    private TossBillingPaymentResponse successResponse() {
        return new TossBillingPaymentResponse(
                "payment-key",
                ORDER_NO,
                "Plus Subscription",
                "DONE",
                19900L,
                "2026-07-15T10:00:00+09:00",
                "2026-07-15T10:00:01+09:00",
                new TossBillingPaymentResponse.TossCardInfo(
                        "41",
                        "41",
                        "123456******7890",
                        "신용",
                        "개인"
                ),
                new TossBillingPaymentResponse.TossReceipt("https://receipt.example")
        );
    }

    private TossPaymentException tossException(String tossCode) {
        return new TossPaymentException(
                HttpStatus.GATEWAY_TIMEOUT,
                tossCode,
                tossCode
        );
    }
}
