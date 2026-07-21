package com.haagendazs.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.PaymentNotificationPayload;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.entity.PaymentCustomerKey;
import com.haagendazs.payment.payment.entity.PaymentRetryJob;
import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.CardType;
import com.haagendazs.payment.payment.enums.OwnerType;
import com.haagendazs.payment.payment.enums.PaymentRetryJobStatus;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.repository.PaymentCustomerKeyRepository;
import com.haagendazs.payment.payment.repository.PaymentRepository;
import com.haagendazs.payment.payment.repository.PaymentRetryJobRepository;
import com.haagendazs.payment.payment.service.BillingCheckoutService;
import com.haagendazs.payment.payment.service.BillingMethodService;
import com.haagendazs.payment.payment.service.BillingPaymentService;
import com.haagendazs.payment.payment.service.BillingPaymentTransactionService;
import com.haagendazs.payment.payment.service.PaymentCustomerKeyService;
import com.haagendazs.payment.payment.service.PaymentRetryJobService;
import com.haagendazs.payment.payment.service.PaymentRetryJobScheduler;
import com.haagendazs.payment.payment.service.PaymentRetryJobTransactionService;
import com.haagendazs.payment.payment.service.dto.BillingMethodIssueAndPayRequest;
import com.haagendazs.payment.payment.service.dto.BillingMethodIssueRequest;
import com.haagendazs.payment.payment.service.dto.BillingPaymentRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueResponse;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.CustomerKeyEncryptor;
import com.haagendazs.payment.payment.service.tools.CustomerKeyHashEncoder;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import com.haagendazs.payment.payment.service.tools.TossCustomerKeyGenerator;
import com.haagendazs.payment.payment.service.tools.TossPaymentException;
import com.haagendazs.payment.product.entity.OrderItems;
import com.haagendazs.payment.product.enums.ProductType;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import com.haagendazs.payment.subscription.enums.SubscriptionStatus;
import com.haagendazs.payment.subscription.repository.SubscriptionScheduledChangesRepository;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    @DisplayName("기본 자동결제 수단 결제 성공 - billing 없이 결제 준비")
    void payWithRegisteredBillingMethodSuccessTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossBillingPaymentResponse response = successResponse();
        when(billingPaymentTransactionService.prepareBillingPayment(MEMBER_ID, ORDER_NO, null))
                .thenReturn(preparation);
        when(tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        )).thenReturn(response);

        billingPaymentService.payWithRegisteredBillingMethod(
                MEMBER_ID,
                new BillingPaymentRequest(ORDER_NO)
        );

        verify(billingPaymentTransactionService)
                .completeBillingPayment(MEMBER_ID, preparation, response);
    }

    @Test
    @DisplayName("정기 갱신 자동결제 성공 - 구독 갱신 완료 후처리")
    void paySubscriptionRenewalWithBillingMethodSuccessTest() {
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

        billingPaymentService.paySubscriptionRenewalWithBillingMethod(MEMBER_ID, ORDER_NO, billing);

        verify(billingPaymentTransactionService)
                .completeSubscriptionRenewalPayment(MEMBER_ID, preparation, response);
        verify(billingPaymentTransactionService, never())
                .completeBillingPayment(MEMBER_ID, preparation, response);
    }

    @Test
    @DisplayName("checkout 자동결제 실패 - Toss 응답 상태가 DONE이 아니면 실패 처리")
    void payCheckoutWithBillingMethodNotDoneResponseTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossBillingPaymentResponse response = response("FAILED", 19900L);
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

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED);

        verify(billingPaymentTransactionService)
                .failBillingPayment(any(), any(), any(BusinessException.class));
    }

    @Test
    @DisplayName("checkout 자동결제 실패 - Toss 응답 금액이 다르면 실패 처리")
    void payCheckoutWithBillingMethodAmountMismatchResponseTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossBillingPaymentResponse response = response("DONE", 9900L);
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

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED);

        verify(billingPaymentTransactionService)
                .failBillingPayment(any(), any(), any(BusinessException.class));
    }

    @Test
    @DisplayName("checkout 자동결제 중 런타임 예외 - 실패 처리 후 원 예외 전파")
    void payCheckoutWithBillingMethodRuntimeExceptionTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        RuntimeException exception = new RuntimeException("toss unavailable");
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
    }

    @Test
    @DisplayName("checkout 자동결제 실패 상태 보정 실패 - 원 예외 전파")
    void payCheckoutWithBillingMethodFailureHandlingExceptionTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        RuntimeException exception = new RuntimeException("toss unavailable");
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
        org.mockito.Mockito.doThrow(new RuntimeException("fail handling failed"))
                .when(billingPaymentTransactionService)
                .failBillingPayment(MEMBER_ID, preparation, exception);

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("checkout 자동결제 성공 후처리 실패 - 보정 필요 상태 전환 후 원 예외 전파")
    void payCheckoutWithBillingMethodCompleteFailureTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossBillingPaymentResponse response = successResponse();
        RuntimeException exception = new RuntimeException("complete failed");
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
        org.mockito.Mockito.doThrow(exception)
                .when(billingPaymentTransactionService)
                .completeBillingPayment(MEMBER_ID, preparation, response);

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);

        verify(billingPaymentTransactionService)
                .markReconcileRequired(MEMBER_ID, preparation, exception);
    }

    @Test
    @DisplayName("checkout 자동결제 성공 후처리와 보정 상태 전환이 모두 실패 - 원 예외 전파")
    void payCheckoutWithBillingMethodCompleteAndReconcileMarkFailureTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossBillingPaymentResponse response = successResponse();
        RuntimeException exception = new RuntimeException("complete failed");
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
        org.mockito.Mockito.doThrow(exception)
                .when(billingPaymentTransactionService)
                .completeBillingPayment(MEMBER_ID, preparation, response);
        org.mockito.Mockito.doThrow(new RuntimeException("reconcile mark failed"))
                .when(billingPaymentTransactionService)
                .markReconcileRequired(MEMBER_ID, preparation, exception);

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);
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
    @DisplayName("정기 갱신 자동결제 일시 실패 상태 보정 실패 - 원 예외 전파")
    void paySubscriptionRenewalWithBillingMethodRetryMarkFailureTest() {
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
        org.mockito.Mockito.doThrow(new RuntimeException("retry mark failed"))
                .when(billingPaymentTransactionService)
                .markRetryScheduled(MEMBER_ID, preparation, exception);

        assertThatThrownBy(() -> billingPaymentService.paySubscriptionRenewalWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);
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
    @DisplayName("정기 갱신 자동결제 read timeout - 결제 조회 결과 DONE이면 구독 갱신 완료 보정")
    void paySubscriptionRenewalWithBillingMethodReadTimeoutReconcileSuccessTest() {
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

        billingPaymentService.paySubscriptionRenewalWithBillingMethod(MEMBER_ID, ORDER_NO, billing);

        verify(billingPaymentTransactionService)
                .completeSubscriptionRenewalPayment(MEMBER_ID, preparation, queriedResponse);
    }

    @Test
    @DisplayName("checkout 자동결제 read timeout - 결제 조회 결과 실패면 실패 처리")
    void payCheckoutWithBillingMethodReadTimeoutReconcilePaymentFailedTest() {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation = preparation();
        TossPaymentException exception = tossException("TOSS_READ_TIMEOUT");
        TossBillingPaymentResponse queriedResponse = response("FAILED", 19900L);
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

        assertThatThrownBy(() -> billingPaymentService.payCheckoutWithBillingMethod(MEMBER_ID, ORDER_NO, billing))
                .isSameAs(exception);

        verify(billingPaymentTransactionService)
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

    @Test
    @DisplayName("customerKey 조회 또는 생성 - 기존 키가 있으면 복호화해서 반환")
    void getOrCreateCustomerKeyExistingTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        TossCustomerKeyGenerator generator = mock(TossCustomerKeyGenerator.class);
        CustomerKeyEncryptor encryptor = realCustomerKeyEncryptor();
        CustomerKeyHashEncoder hashEncoder = realCustomerKeyHashEncoder();
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(repository, generator, encryptor, hashEncoder);
        String rawCustomerKey = "raw-customer-key";
        PaymentCustomerKey customerKey = PaymentCustomerKey.create(
                1L,
                encryptor.encrypt(rawCustomerKey),
                hashEncoder.encode(rawCustomerKey)
        );
        when(repository.findByMemberId(1L)).thenReturn(Optional.of(customerKey));

        String result = service.getOrCreateCustomerKey(1L);

        assertThat(result).isEqualTo(rawCustomerKey);
        verify(generator, never()).generate();
        verify(repository, never()).save(any(PaymentCustomerKey.class));
    }

    @Test
    @DisplayName("customerKey 조회 또는 생성 - 없으면 유니크 키를 암호화 저장")
    void getOrCreateCustomerKeyCreateTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        TossCustomerKeyGenerator generator = new TossCustomerKeyGenerator();
        CustomerKeyEncryptor encryptor = realCustomerKeyEncryptor();
        CustomerKeyHashEncoder hashEncoder = realCustomerKeyHashEncoder();
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(repository, generator, encryptor, hashEncoder);
        when(repository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(repository.existsByCustomerKeyHash(any())).thenReturn(false);

        String result = service.getOrCreateCustomerKey(1L);

        ArgumentCaptor<PaymentCustomerKey> customerKeyCaptor =
                ArgumentCaptor.forClass(PaymentCustomerKey.class);
        verify(repository).save(customerKeyCaptor.capture());
        PaymentCustomerKey saved = customerKeyCaptor.getValue();
        assertThat(result).startsWith("ck_").endsWith(".A1");
        assertThat(encryptor.decrypt(saved.getCustomerKeyEncrypted())).isEqualTo(result);
        assertThat(saved.getCustomerKeyHash()).isEqualTo(hashEncoder.encode(result));
    }

    @Test
    @DisplayName("customerKey 생성 - 5회 모두 중복이면 예외")
    void getOrCreateCustomerKeyGenerationFailedTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        TossCustomerKeyGenerator generator = mock(TossCustomerKeyGenerator.class);
        CustomerKeyEncryptor encryptor = mock(CustomerKeyEncryptor.class);
        CustomerKeyHashEncoder hashEncoder = mock(CustomerKeyHashEncoder.class);
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(repository, generator, encryptor, hashEncoder);
        when(repository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(generator.generate()).thenReturn("duplicated-customer-key");
        when(hashEncoder.encode("duplicated-customer-key")).thenReturn("duplicated-hash");
        when(repository.existsByCustomerKeyHash("duplicated-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.getOrCreateCustomerKey(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.CUSTOMER_KEY_GENERATION_FAILED);
        verify(repository, never()).save(any(PaymentCustomerKey.class));
    }

    @Test
    @DisplayName("customerKey 조회 - 없으면 예외")
    void getCustomerKeyNotFoundTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(
                repository,
                mock(TossCustomerKeyGenerator.class),
                mock(CustomerKeyEncryptor.class),
                mock(CustomerKeyHashEncoder.class)
        );
        when(repository.findByMemberId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerKey(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.CUSTOMER_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("customerKey tools - 암호화/복호화와 해시 생성")
    void customerKeyToolsTest() {
        CustomerKeyEncryptor encryptor = realCustomerKeyEncryptor();
        CustomerKeyHashEncoder hashEncoder = realCustomerKeyHashEncoder();
        String rawCustomerKey = "ck_test-customer-key.A1";

        String encrypted = encryptor.encrypt(rawCustomerKey);
        String encryptedAgain = encryptor.encrypt(rawCustomerKey);
        String hash = hashEncoder.encode(rawCustomerKey);

        assertThat(encrypted).isNotEqualTo(rawCustomerKey);
        assertThat(encryptedAgain).isNotEqualTo(encrypted);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(rawCustomerKey);
        assertThat(hash).isEqualTo(hashEncoder.encode(rawCustomerKey));
        assertThat(hash).isNotEqualTo(hashEncoder.encode("different-customer-key"));
    }

    @Test
    @DisplayName("customerKey tools - Toss customerKey 생성 포맷")
    void tossCustomerKeyGeneratorTest() {
        TossCustomerKeyGenerator generator = new TossCustomerKeyGenerator();

        String customerKey = generator.generate();

        assertThat(customerKey).startsWith("ck_").endsWith(".A1");
        assertThat(customerKey).contains("_");
        assertThat(customerKey).contains(".");
    }

    @Test
    @DisplayName("billing 등록 준비 - customerKey 생성 결과 반환")
    void prepareRegistrationTest() {
        PaymentCustomerKeyService customerKeyService = mock(PaymentCustomerKeyService.class);
        BillingMethodService service = new BillingMethodService(
                customerKeyService,
                mock(BillingRepository.class),
                mock(TossBillingClient.class)
        );
        when(customerKeyService.getOrCreateCustomerKey(1L)).thenReturn("customer-key");

        assertThat(service.prepareRegistration(1L).customerKey()).isEqualTo("customer-key");
    }

    @Test
    @DisplayName("billing 발급 - 첫 결제수단은 기본 결제수단으로 저장")
    void issueBillingMethodFirstDefaultTest() {
        PaymentCustomerKeyService customerKeyService = mock(PaymentCustomerKeyService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        TossBillingClient tossBillingClient = mock(TossBillingClient.class);
        BillingMethodService service = new BillingMethodService(customerKeyService, billingRepository, tossBillingClient);
        TossBillingKeyIssueResponse tossResponse = tossBillingKeyIssueResponse();
        when(billingRepository.countByMemberIdAndBillingStatus(1L, BillingStatus.ACTIVE)).thenReturn(0L);
        when(customerKeyService.getCustomerKey(1L)).thenReturn("customer-key");
        when(tossBillingClient.issueBillingKey("auth-key", "customer-key")).thenReturn(tossResponse);
        when(billingRepository.save(any(Billing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Billing billing = service.issueBillingMethod(1L, new BillingMethodIssueRequest("auth-key"));

        assertThat(billing.getBillingKey()).isEqualTo("billing-key");
        assertThat(billing.getIssuerCode()).isEqualTo(CardCompany.SHINHAN);
        assertThat(billing.getCardType()).isEqualTo(CardType.CREDIT);
        assertThat(billing.getOwnerType()).isEqualTo(OwnerType.INDIVIDUAL);
        assertThat(billing.getIsDefault()).isTrue();
        assertThat(billing.getBillingStatus()).isEqualTo(BillingStatus.ACTIVE);
    }

    @Test
    @DisplayName("billing 발급 - 한도 초과시 예외")
    void issueBillingMethodLimitExceededTest() {
        BillingRepository billingRepository = mock(BillingRepository.class);
        BillingMethodService service = new BillingMethodService(
                mock(PaymentCustomerKeyService.class),
                billingRepository,
                mock(TossBillingClient.class)
        );
        when(billingRepository.countByMemberIdAndBillingStatus(1L, BillingStatus.ACTIVE)).thenReturn(5L);

        assertThatThrownBy(() -> service.issueBillingMethod(1L, new BillingMethodIssueRequest("auth-key")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.BILLING_METHOD_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("billing 삭제 - Toss 삭제 성공 후 비활성화하고 단일 활성 수단을 기본값으로 보정")
    void deleteBillingMethodTest() {
        BillingRepository billingRepository = mock(BillingRepository.class);
        TossBillingClient tossBillingClient = mock(TossBillingClient.class);
        BillingMethodService service = new BillingMethodService(
                mock(PaymentCustomerKeyService.class),
                billingRepository,
                tossBillingClient
        );
        Billing deletingBilling = Billing.builder()
                .id(10L)
                .memberId(1L)
                .billingKey("delete-billing-key")
                .billingStatus(BillingStatus.ACTIVE)
                .isDefault(true)
                .build();
        Billing remainingBilling = Billing.builder()
                .id(11L)
                .memberId(1L)
                .billingKey("remaining-billing-key")
                .billingStatus(BillingStatus.ACTIVE)
                .isDefault(false)
                .build();
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(deletingBilling));
        when(billingRepository.findByMemberIdAndBillingStatus(1L, BillingStatus.ACTIVE))
                .thenReturn(List.of(remainingBilling));

        service.deleteBillingMethod(1L, 10L);

        verify(tossBillingClient).deleteBillingKey("delete-billing-key");
        assertThat(deletingBilling.getBillingStatus()).isEqualTo(BillingStatus.INACTIVE);
        assertThat(deletingBilling.getBillingKey()).isNull();
        assertThat(remainingBilling.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("billing 삭제 - Toss 삭제 실패시 예외")
    void deleteBillingMethodTossFailureTest() {
        BillingRepository billingRepository = mock(BillingRepository.class);
        TossBillingClient tossBillingClient = mock(TossBillingClient.class);
        BillingMethodService service = new BillingMethodService(
                mock(PaymentCustomerKeyService.class),
                billingRepository,
                tossBillingClient
        );
        Billing billing = Billing.builder()
                .id(10L)
                .memberId(1L)
                .billingKey("billing-key")
                .billingStatus(BillingStatus.ACTIVE)
                .isDefault(true)
                .build();
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        org.mockito.Mockito.doThrow(new RestClientException("delete failed"))
                .when(tossBillingClient)
                .deleteBillingKey("billing-key");

        assertThatThrownBy(() -> service.deleteBillingMethod(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.BILLING_KEY_DELETE_FAILED);
    }

    @Test
    @DisplayName("billing 발급 후 결제 - 방금 등록한 billing으로 checkout 결제")
    void issueBillingMethodAndPayTest() {
        BillingMethodService billingMethodService = mock(BillingMethodService.class);
        BillingPaymentService billingPaymentService = mock(BillingPaymentService.class);
        BillingCheckoutService service = new BillingCheckoutService(billingMethodService, billingPaymentService);
        Billing billing = activeBilling();
        when(billingMethodService.issueBillingMethod(eq(1L), any(BillingMethodIssueRequest.class)))
                .thenReturn(billing);

        service.issueBillingMethodAndPay(1L, new BillingMethodIssueAndPayRequest("auth-key", ORDER_NO));

        verify(billingMethodService).issueBillingMethod(eq(1L), any(BillingMethodIssueRequest.class));
        verify(billingPaymentService).payCheckoutWithBillingMethod(1L, ORDER_NO, billing);
    }

    @Test
    @DisplayName("결제 재시도 예약 - 첫 재시도 작업 생성 요청")
    void scheduleRetryTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        PaymentRetryJobService service = new PaymentRetryJobService(
                transactionService,
                mock(BillingPaymentService.class),
                mock(BillingRepository.class)
        );
        TossPaymentException exception = tossException("TOSS_CONNECT_TIMEOUT");

        service.scheduleRetry(1L, ORDER_NO, 10L, exception);

        verify(transactionService).scheduleRetry(
                eq(1L),
                eq(ORDER_NO),
                eq(10L),
                eq(3),
                any(LocalDateTime.class),
                eq("TOSS_CONNECT_TIMEOUT"),
                eq("TOSS_CONNECT_TIMEOUT")
        );
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 결제 성공시 작업 성공 상태")
    void processDueRetryJobsSuccessTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        BillingPaymentService paymentService = mock(BillingPaymentService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        PaymentRetryJobService service = new PaymentRetryJobService(transactionService, paymentService, billingRepository);
        Billing billing = activeBilling();
        PaymentRetryJobTransactionService.PaymentRetryJobSnapshot snapshot = retrySnapshot(1, 3);
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(snapshot);
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));

        service.processDueRetryJobs();

        verify(paymentService).paySubscriptionRenewalWithBillingMethod(1L, ORDER_NO, billing);
        verify(transactionService).succeedRetryJob(100L);
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 일시 오류면 다음 재시도 예약")
    void processDueRetryJobsRetryLaterTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        BillingPaymentService paymentService = mock(BillingPaymentService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        PaymentRetryJobService service = new PaymentRetryJobService(transactionService, paymentService, billingRepository);
        Billing billing = activeBilling();
        TossPaymentException exception = tossException("TOSS_CONNECT_TIMEOUT");
        PaymentRetryJobTransactionService.PaymentRetryJobSnapshot snapshot = retrySnapshot(0, 3);
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(snapshot);
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        org.mockito.Mockito.doThrow(exception)
                .when(paymentService)
                .paySubscriptionRenewalWithBillingMethod(1L, ORDER_NO, billing);

        service.processDueRetryJobs();

        verify(transactionService).rescheduleRetryJob(
                eq(100L),
                any(LocalDateTime.class),
                eq("TOSS_CONNECT_TIMEOUT"),
                eq("TOSS_CONNECT_TIMEOUT")
        );
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 재시도 한도 도달시 최종 실패")
    void processDueRetryJobsMaxRetryFailTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        BillingPaymentService paymentService = mock(BillingPaymentService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        PaymentRetryJobService service = new PaymentRetryJobService(transactionService, paymentService, billingRepository);
        Billing billing = activeBilling();
        TossPaymentException exception = tossException("TOSS_CONNECT_TIMEOUT");
        PaymentRetryJobTransactionService.PaymentRetryJobSnapshot snapshot = retrySnapshot(2, 3);
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(snapshot);
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        org.mockito.Mockito.doThrow(exception)
                .when(paymentService)
                .paySubscriptionRenewalWithBillingMethod(1L, ORDER_NO, billing);

        service.processDueRetryJobs();

        verify(transactionService).failRetryJob(100L, "TOSS_CONNECT_TIMEOUT", "TOSS_CONNECT_TIMEOUT");
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 활성 billing이 없으면 최종 실패")
    void processDueRetryJobsBillingNotFoundTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        PaymentRetryJobService service = new PaymentRetryJobService(
                transactionService,
                mock(BillingPaymentService.class),
                mock(BillingRepository.class)
        );
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(retrySnapshot(0, 3));

        service.processDueRetryJobs();

        verify(transactionService).failRetryJob(
                100L,
                PaymentErrorCode.BILLING_METHOD_NOT_FOUND.getCode(),
                PaymentErrorCode.BILLING_METHOD_NOT_FOUND.getMessage()
        );
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 비일시 오류면 최종 실패")
    void processDueRetryJobsNonRetryableFailTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        BillingPaymentService paymentService = mock(BillingPaymentService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        PaymentRetryJobService service = new PaymentRetryJobService(transactionService, paymentService, billingRepository);
        Billing billing = activeBilling();
        TossPaymentException exception = tossException("REJECT_CARD_PAYMENT");
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(retrySnapshot(0, 3));
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        org.mockito.Mockito.doThrow(exception)
                .when(paymentService)
                .paySubscriptionRenewalWithBillingMethod(1L, ORDER_NO, billing);

        service.processDueRetryJobs();

        verify(transactionService).failRetryJob(100L, "REJECT_CARD_PAYMENT", "REJECT_CARD_PAYMENT");
    }

    @Test
    @DisplayName("재시도 작업 예약 트랜잭션 - 주문 재시도 예약과 구독 결제 유예 상태 전환")
    void scheduleRetryTransactionTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionsRepository subscriptionsRepository = mock(SubscriptionsRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                mock(PaymentEventProducer.class)
        );
        Orders order = pendingBillingOrder();
        Subscriptions subscription = activeSubscription();
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(10);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(subscriptionsRepository.findByWorkspaceId(1L)).thenReturn(Optional.of(subscription));
        when(retryJobRepository.save(any(PaymentRetryJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRetryJob retryJob = service.scheduleRetry(
                1L,
                ORDER_NO,
                10L,
                3,
                nextRetryAt,
                "TOSS_CONNECT_TIMEOUT",
                "timeout"
        );

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.RETRY_SCHEDULED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(retryJob.getMemberId()).isEqualTo(1L);
        assertThat(retryJob.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(retryJob.getBillingId()).isEqualTo(10L);
        assertThat(retryJob.getRetryCount()).isZero();
        assertThat(retryJob.getMaxRetryCount()).isEqualTo(3);
        assertThat(retryJob.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.SCHEDULED);
        assertThat(retryJob.getLastErrorCode()).isEqualTo("TOSS_CONNECT_TIMEOUT");
    }

    @Test
    @DisplayName("재시도 작업 조회 트랜잭션 - 만료된 작업 ID만 반환")
    void findDueRetryJobIdsTransactionTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        LocalDateTime now = LocalDateTime.now();
        PaymentRetryJob firstJob = retryJob(100L, PaymentRetryJobStatus.SCHEDULED, now.minusMinutes(1), 0);
        PaymentRetryJob secondJob = retryJob(101L, PaymentRetryJobStatus.SCHEDULED, now.minusMinutes(1), 0);
        when(retryJobRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                eq(PaymentRetryJobStatus.SCHEDULED),
                eq(now),
                any()
        )).thenReturn(List.of(firstJob, secondJob));

        List<Long> retryJobIds = service.findDueRetryJobIds(now, 50);

        assertThat(retryJobIds).containsExactly(100L, 101L);
    }

    @Test
    @DisplayName("재시도 작업 시작 트랜잭션 - 실행중 상태로 전환하고 스냅샷 반환")
    void startRetryJobTransactionTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        LocalDateTime now = LocalDateTime.now();
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.SCHEDULED, now.minusMinutes(1), 1);
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));

        PaymentRetryJobTransactionService.PaymentRetryJobSnapshot snapshot =
                service.startRetryJob(100L, now);

        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.RUNNING);
        assertThat(retryJob.getLastTriedAt()).isEqualTo(now);
        assertThat(snapshot.retryJobId()).isEqualTo(100L);
        assertThat(snapshot.retryCount()).isEqualTo(1);
        assertThat(snapshot.maxRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("재시도 작업 시작 트랜잭션 - 실행 대상이 아니면 예외")
    void startRetryJobInvalidStatusTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now().minusMinutes(1), 1);
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));

        assertThatThrownBy(() -> service.startRetryJob(100L, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("재시도 작업 성공 트랜잭션 - 성공 상태 전환")
    void succeedRetryJobTransactionTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 1);
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));

        service.succeedRetryJob(100L);

        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("재시도 작업 재예약 트랜잭션 - retryCount 증가와 구독 결제 유예 상태 전환")
    void rescheduleRetryJobTransactionTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionsRepository subscriptionsRepository = mock(SubscriptionsRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                mock(PaymentEventProducer.class)
        );
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 1);
        Orders order = processingBillingOrder();
        Subscriptions subscription = activeSubscription();
        LocalDateTime nextRetryAt = LocalDateTime.now().plusHours(1);
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(subscriptionsRepository.findByWorkspaceId(1L)).thenReturn(Optional.of(subscription));

        service.rescheduleRetryJob(100L, nextRetryAt, "TOSS_CONNECT_TIMEOUT", "timeout");

        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.SCHEDULED);
        assertThat(retryJob.getRetryCount()).isEqualTo(2);
        assertThat(retryJob.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.RETRY_SCHEDULED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    @DisplayName("재시도 작업 최종 실패 트랜잭션 - 작업/주문 실패와 구독 만료")
    void failRetryJobTransactionTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionsRepository subscriptionsRepository = mock(SubscriptionsRepository.class);
        SubscriptionScheduledChangesRepository scheduledChangesRepository =
                mock(SubscriptionScheduledChangesRepository.class);
        PaymentEventProducer paymentEventProducer = mock(PaymentEventProducer.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                scheduledChangesRepository,
                paymentEventProducer
        );
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 2);
        Orders order = processingBillingOrder();
        Subscriptions subscription = activeSubscription();
        SubscriptionScheduledChanges scheduledChange = SubscriptionScheduledChanges.builder()
                .subscriptionId(subscription.getId())
                .changeType(SubscriptionChangeType.PLAN_CHANGE)
                .changeStatus(SubscriptionChangeStatus.SCHEDULED)
                .requestedAt(LocalDateTime.now().minusDays(1))
                .scheduledAt(LocalDateTime.now().minusHours(1))
                .memberId(1L)
                .requestedPlanId(2L)
                .build();
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(subscriptionsRepository.findByWorkspaceId(1L)).thenReturn(Optional.of(subscription));
        when(scheduledChangesRepository.findBySubscriptionIdAndChangeTypeAndChangeStatus(
                subscription.getId(),
                SubscriptionChangeType.PLAN_CHANGE,
                SubscriptionChangeStatus.SCHEDULED
        )).thenReturn(List.of(scheduledChange));

        service.failRetryJob(100L, "REJECT_CARD_PAYMENT", "rejected");

        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.FAILED);
        assertThat(retryJob.getRetryCount()).isEqualTo(3);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(scheduledChange.getChangeStatus()).isEqualTo(SubscriptionChangeStatus.CANCELED);
        assertThat(scheduledChange.getCanceledAt()).isNotNull();
        verify(paymentEventProducer).publishPaymentFailed(eq(1L), any(PaymentNotificationPayload.class));
    }

    @Test
    @DisplayName("billing 결제 준비 - 기본 결제수단으로 주문 처리중 전환")
    void prepareBillingPaymentDefaultBillingTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = pendingBillingOrder();
        Billing billing = activeBilling();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(transactionBillingRepository.findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                1L,
                BillingStatus.ACTIVE
        )).thenReturn(Optional.of(billing));
        when(transactionCustomerKeyService.getCustomerKey(1L)).thenReturn("customer-key");

        BillingPaymentTransactionService.BillingPaymentPreparation preparation =
                service.prepareBillingPayment(1L, ORDER_NO, null);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(preparation.orderId()).isEqualTo(order.getId());
        assertThat(preparation.billingId()).isEqualTo(10L);
        assertThat(preparation.idempotencyKey()).isEqualTo("billing-payment-" + ORDER_NO);
    }

    @Test
    @DisplayName("billing 결제 준비 - 재시도 예약 주문도 결제 준비 가능")
    void prepareBillingPaymentRetryScheduledOrderTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = pendingBillingOrder();
        order.markRetryScheduled();
        Billing billing = activeBilling();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(transactionBillingRepository.findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                1L,
                BillingStatus.ACTIVE
        )).thenReturn(Optional.of(billing));
        when(transactionCustomerKeyService.getCustomerKey(1L)).thenReturn("customer-key");

        BillingPaymentTransactionService.BillingPaymentPreparation preparation =
                service.prepareBillingPayment(1L, ORDER_NO, null);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(preparation.orderNo()).isEqualTo(ORDER_NO);
    }

    @Test
    @DisplayName("billing 결제 준비 - 주문 소유자가 아니면 예외")
    void prepareBillingPaymentAccessDeniedTest() {
        BillingPaymentTransactionService service = transactionService();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(pendingBillingOrder()));

        assertThatThrownBy(() -> service.prepareBillingPayment(999L, ORDER_NO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("billing 결제 준비 - 주문이 없으면 예외")
    void prepareBillingPaymentOrderNotFoundTest() {
        BillingPaymentTransactionService service = transactionService();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepareBillingPayment(1L, ORDER_NO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("billing 결제 준비 - 결제 가능한 주문 상태가 아니면 예외")
    void prepareBillingPaymentInvalidOrderStatusTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = pendingBillingOrder();
        order.complete();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.prepareBillingPayment(1L, ORDER_NO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("billing 결제 준비 - billing 주문이 아니면 예외")
    void prepareBillingPaymentInvalidOrderTypeTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = Orders.builder()
                .id(1L)
                .memberId(1L)
                .workspaceId(1L)
                .orderNo(ORDER_NO)
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.Normal)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.prepareBillingPayment(1L, ORDER_NO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_TYPE);
    }

    @Test
    @DisplayName("billing 결제 준비 - 만료된 주문이면 예외")
    void prepareBillingPaymentExpiredOrderTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = Orders.builder()
                .id(1L)
                .memberId(1L)
                .workspaceId(1L)
                .orderNo(ORDER_NO)
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.Billing)
                .orderedAt(LocalDateTime.now().minusHours(1))
                .expiredAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.prepareBillingPayment(1L, ORDER_NO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_EXPIRED);
    }

    @Test
    @DisplayName("billing 결제 준비 - 요청 billing을 직접 지정")
    void prepareBillingPaymentRequestedBillingTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = pendingBillingOrder();
        Billing requestedBilling = activeBilling();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(transactionBillingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(requestedBilling));
        when(transactionCustomerKeyService.getCustomerKey(1L)).thenReturn("customer-key");

        BillingPaymentTransactionService.BillingPaymentPreparation preparation =
                service.prepareBillingPayment(1L, ORDER_NO, requestedBilling);

        assertThat(preparation.billingId()).isEqualTo(10L);
        assertThat(preparation.billingKey()).isEqualTo("billing-key");
    }

    @Test
    @DisplayName("billing 결제 준비 - 기본 결제수단이 없으면 예외")
    void prepareBillingPaymentDefaultBillingNotFoundTest() {
        BillingPaymentTransactionService service = transactionService();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(pendingBillingOrder()));
        when(transactionBillingRepository.findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                1L,
                BillingStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepareBillingPayment(1L, ORDER_NO, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.BILLING_METHOD_NOT_FOUND);
    }

    @Test
    @DisplayName("billing 결제 준비 - 요청 billing이 활성 상태로 없으면 예외")
    void prepareBillingPaymentRequestedBillingNotFoundTest() {
        BillingPaymentTransactionService service = transactionService();
        Billing requestedBilling = activeBilling();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(pendingBillingOrder()));
        when(transactionBillingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepareBillingPayment(1L, ORDER_NO, requestedBilling))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.BILLING_METHOD_NOT_FOUND);
    }

    @Test
    @DisplayName("billing 결제 완료 - 결제 저장, 주문 완료, 구독 활성화, 완료 이벤트 발행")
    void completeBillingPaymentTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        Billing billing = activeBilling();
        TossBillingPaymentResponse response = successResponse();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(transactionBillingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        when(transactionPaymentRepository.save(any(Payments.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeBillingPayment(1L, transactionPreparation(), response);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(transactionSubscriptionService).activateSubscriptionByPayment(order, billing);
        verify(transactionPaymentRepository).save(any(Payments.class));
        verify(transactionEventProducer).publishPaymentCompleted(eq(1L), any(PaymentNotificationPayload.class));
    }

    @Test
    @DisplayName("billing 결제 완료 - 성공 이벤트 발행 실패는 삼킴")
    void completeBillingPaymentPublishFailureTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        Billing billing = activeBilling();
        TossBillingPaymentResponse response = successResponse();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(transactionBillingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        when(transactionPaymentRepository.save(any(Payments.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("kafka failed"))
                .when(transactionEventProducer)
                .publishPaymentCompleted(eq(1L), any(PaymentNotificationPayload.class));

        service.completeBillingPayment(1L, transactionPreparation(), response);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(transactionSubscriptionService).activateSubscriptionByPayment(order, billing);
    }

    @Test
    @DisplayName("정기 갱신 결제 완료 - 구독 갱신 후처리")
    void completeSubscriptionRenewalPaymentTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        Billing billing = activeBilling();
        TossBillingPaymentResponse response = successResponse();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(transactionBillingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        when(transactionPaymentRepository.save(any(Payments.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeSubscriptionRenewalPayment(1L, transactionPreparation(), response);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(transactionSubscriptionService).renewSubscriptionByPayment(order, billing);
        verify(transactionEventProducer).publishPaymentCompleted(eq(1L), any(PaymentNotificationPayload.class));
    }

    @Test
    @DisplayName("billing 결제 실패 - 주문 실패 상태와 실패 이벤트 발행")
    void failBillingPaymentTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        RuntimeException exception = new RuntimeException("payment failed");
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        service.failBillingPayment(1L, transactionPreparation(), exception);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        verify(transactionEventProducer).publishPaymentFailed(eq(1L), any(PaymentNotificationPayload.class));
    }

    @Test
    @DisplayName("billing 결제 실패 - 실패 이벤트 발행 실패는 삼킴")
    void failBillingPaymentPublishFailureTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        RuntimeException exception = new RuntimeException("payment failed");
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(new RuntimeException("kafka failed"))
                .when(transactionEventProducer)
                .publishPaymentFailed(eq(1L), any(PaymentNotificationPayload.class));

        service.failBillingPayment(1L, transactionPreparation(), exception);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("billing 결제 실패 - BusinessException이면 errorCode를 실패 코드로 사용")
    void failBillingPaymentBusinessExceptionCodeTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        BusinessException exception = new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
        ArgumentCaptor<PaymentNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(PaymentNotificationPayload.class);
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        service.failBillingPayment(1L, transactionPreparation(), exception);

        verify(transactionEventProducer).publishPaymentFailed(eq(1L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().failCode()).isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED.getCode());
    }

    @Test
    @DisplayName("billing 결제 재시도 예약 - 주문 재시도 예약 상태와 실패 이벤트 발행")
    void markRetryScheduledTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        RuntimeException exception = new RuntimeException("retry later");
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        service.markRetryScheduled(1L, transactionPreparation(), exception);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.RETRY_SCHEDULED);
        verify(transactionEventProducer).publishPaymentFailed(eq(1L), any(PaymentNotificationPayload.class));
    }

    @Test
    @DisplayName("billing 결제 보정 필요 - 주문 보정 필요 상태와 실패 이벤트 발행")
    void markReconcileRequiredTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = processingBillingOrder();
        RuntimeException exception = new RuntimeException("reconcile required");
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        service.markReconcileRequired(1L, transactionPreparation(), exception);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.RECONCILE_REQUIRED);
        verify(transactionEventProducer).publishPaymentFailed(eq(1L), any(PaymentNotificationPayload.class));
    }

    @Test
    @DisplayName("billing 후처리 - 처리중 주문이 아니면 예외")
    void getProcessingOrderInvalidStatusTest() {
        BillingPaymentTransactionService service = transactionService();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(pendingBillingOrder()));

        assertThatThrownBy(() -> service.failBillingPayment(1L, transactionPreparation(), new RuntimeException("failed")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("billing 후처리 - 주문이 없으면 예외")
    void getProcessingOrderNotFoundTest() {
        BillingPaymentTransactionService service = transactionService();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.failBillingPayment(1L, transactionPreparation(), new RuntimeException("failed")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("billing 후처리 - 주문 소유자가 아니면 예외")
    void getProcessingOrderAccessDeniedTest() {
        BillingPaymentTransactionService service = transactionService();
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(processingBillingOrder()));

        assertThatThrownBy(() -> service.failBillingPayment(999L, transactionPreparation(), new RuntimeException("failed")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("billing 결제 실패 - 주문 아이템이 없으면 알림 상품명은 null")
    void failBillingPaymentWithoutOrderItemsTest() {
        BillingPaymentTransactionService service = transactionService();
        Orders order = Orders.builder()
                .id(1L)
                .memberId(1L)
                .workspaceId(1L)
                .orderNo(ORDER_NO)
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PROCESSING)
                .orderType(OrderType.Billing)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();
        ArgumentCaptor<PaymentNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(PaymentNotificationPayload.class);
        when(transactionOrderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        service.failBillingPayment(1L, transactionPreparation(), new RuntimeException("payment failed"));

        verify(transactionEventProducer).publishPaymentFailed(eq(1L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().itemName()).isNull();
    }

    @Test
    @DisplayName("billing 결제 실패 알림 - 주문 정보가 없으면 payload 주문 필드는 null")
    void publishPaymentFailedWithoutOrderTest() {
        BillingPaymentTransactionService service = transactionService();
        ArgumentCaptor<PaymentNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(PaymentNotificationPayload.class);

        ReflectionTestUtils.invokeMethod(
                service,
                "publishPaymentFailed",
                1L,
                ORDER_NO,
                null,
                new RuntimeException("payment failed")
        );

        verify(transactionEventProducer).publishPaymentFailed(eq(1L), payloadCaptor.capture());
        PaymentNotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.orderId()).isNull();
        assertThat(payload.workspaceId()).isNull();
        assertThat(payload.totalAmount()).isNull();
        assertThat(payload.itemName()).isNull();
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 일반 런타임 예외면 예외 클래스명을 실패 코드로 사용")
    void processDueRetryJobsRuntimeExceptionFailCodeTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        BillingPaymentService paymentService = mock(BillingPaymentService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        PaymentRetryJobService service = new PaymentRetryJobService(transactionService, paymentService, billingRepository);
        Billing billing = activeBilling();
        RuntimeException exception = new IllegalStateException("unexpected");
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(retrySnapshot(0, 3));
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        org.mockito.Mockito.doThrow(exception)
                .when(paymentService)
                .paySubscriptionRenewalWithBillingMethod(1L, ORDER_NO, billing);

        service.processDueRetryJobs();

        verify(transactionService).failRetryJob(100L, "IllegalStateException", "unexpected");
    }

    @Test
    @DisplayName("만료된 재시도 작업 처리 - 비즈니스 예외면 errorCode를 실패 코드로 사용")
    void processDueRetryJobsBusinessExceptionFailCodeTest() {
        PaymentRetryJobTransactionService transactionService = mock(PaymentRetryJobTransactionService.class);
        BillingPaymentService paymentService = mock(BillingPaymentService.class);
        BillingRepository billingRepository = mock(BillingRepository.class);
        PaymentRetryJobService service = new PaymentRetryJobService(transactionService, paymentService, billingRepository);
        Billing billing = activeBilling();
        BusinessException exception = new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
        when(transactionService.findDueRetryJobIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of(100L));
        when(transactionService.startRetryJob(eq(100L), any(LocalDateTime.class))).thenReturn(retrySnapshot(0, 3));
        when(billingRepository.findByIdAndMemberIdAndBillingStatus(10L, 1L, BillingStatus.ACTIVE))
                .thenReturn(Optional.of(billing));
        org.mockito.Mockito.doThrow(exception)
                .when(paymentService)
                .paySubscriptionRenewalWithBillingMethod(1L, ORDER_NO, billing);

        service.processDueRetryJobs();

        verify(transactionService).failRetryJob(
                100L,
                PaymentErrorCode.BILLING_PAYMENT_FAILED.getCode(),
                PaymentErrorCode.BILLING_PAYMENT_FAILED.getMessage()
        );
    }

    @Test
    @DisplayName("재시도 작업 스케줄러 - 서비스 처리 위임")
    void paymentRetryJobSchedulerTest() {
        PaymentRetryJobService service = mock(PaymentRetryJobService.class);
        PaymentRetryJobScheduler scheduler = new PaymentRetryJobScheduler(service);

        scheduler.processPaymentRetryJobs();

        verify(service).processDueRetryJobs();
    }

    @Test
    @DisplayName("재시도 작업 시작 트랜잭션 - 실행 시간이 아직 아니면 예외")
    void startRetryJobFutureNextRetryAtTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        LocalDateTime now = LocalDateTime.now();
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.SCHEDULED, now.plusMinutes(1), 0);
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));

        assertThatThrownBy(() -> service.startRetryJob(100L, now))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("재시도 작업 트랜잭션 - 작업이 없으면 예외")
    void retryJobNotFoundTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        when(retryJobRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.succeedRetryJob(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("재시도 작업 예약 트랜잭션 - 주문이 없으면 예외")
    void scheduleRetryTransactionOrderNotFoundTest() {
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                mock(PaymentRetryJobRepository.class),
                mock(OrderRepository.class),
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );

        assertThatThrownBy(() -> service.scheduleRetry(
                1L,
                ORDER_NO,
                10L,
                3,
                LocalDateTime.now().plusMinutes(10),
                "ERROR",
                "error"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("재시도 작업 예약 트랜잭션 - 주문 소유자가 아니면 예외")
    void scheduleRetryTransactionAccessDeniedTest() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                mock(PaymentRetryJobRepository.class),
                orderRepository,
                mock(SubscriptionsRepository.class),
                mock(PaymentEventProducer.class)
        );
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(pendingBillingOrder()));

        assertThatThrownBy(() -> service.scheduleRetry(
                999L,
                ORDER_NO,
                10L,
                3,
                LocalDateTime.now().plusMinutes(10),
                "ERROR",
                "error"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("재시도 작업 예약 트랜잭션 - 구독이 없으면 예외")
    void scheduleRetryTransactionSubscriptionNotFoundTest() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionsRepository subscriptionsRepository = mock(SubscriptionsRepository.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                mock(PaymentRetryJobRepository.class),
                orderRepository,
                subscriptionsRepository,
                mock(PaymentEventProducer.class)
        );
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(pendingBillingOrder()));
        when(subscriptionsRepository.findByWorkspaceId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scheduleRetry(
                1L,
                ORDER_NO,
                10L,
                3,
                LocalDateTime.now().plusMinutes(10),
                "ERROR",
                "error"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("재시도 작업 최종 실패 트랜잭션 - 실패 이벤트 발행 실패는 삼킴")
    void failRetryJobPublishFailureTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionsRepository subscriptionsRepository = mock(SubscriptionsRepository.class);
        PaymentEventProducer paymentEventProducer = mock(PaymentEventProducer.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                paymentEventProducer
        );
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 2);
        Orders order = processingBillingOrder();
        Subscriptions subscription = activeSubscription();
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(subscriptionsRepository.findByWorkspaceId(1L)).thenReturn(Optional.of(subscription));
        org.mockito.Mockito.doThrow(new RuntimeException("kafka failed"))
                .when(paymentEventProducer)
                .publishPaymentFailed(eq(1L), any(PaymentNotificationPayload.class));

        service.failRetryJob(100L, "REJECT_CARD_PAYMENT", "rejected");

        assertThat(retryJob.getStatus()).isEqualTo(PaymentRetryJobStatus.FAILED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("재시도 작업 최종 실패 트랜잭션 - 주문 아이템이 없으면 알림 상품명은 null")
    void failRetryJobWithoutOrderItemsTest() {
        PaymentRetryJobRepository retryJobRepository = mock(PaymentRetryJobRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        SubscriptionsRepository subscriptionsRepository = mock(SubscriptionsRepository.class);
        PaymentEventProducer paymentEventProducer = mock(PaymentEventProducer.class);
        PaymentRetryJobTransactionService service = retryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                paymentEventProducer
        );
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 2);
        Orders order = Orders.builder()
                .id(1L)
                .memberId(1L)
                .workspaceId(1L)
                .orderNo(ORDER_NO)
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PROCESSING)
                .orderType(OrderType.Billing)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();
        Subscriptions subscription = activeSubscription();
        ArgumentCaptor<PaymentNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(PaymentNotificationPayload.class);
        when(retryJobRepository.findById(100L)).thenReturn(Optional.of(retryJob));
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));
        when(subscriptionsRepository.findByWorkspaceId(1L)).thenReturn(Optional.of(subscription));

        service.failRetryJob(100L, "REJECT_CARD_PAYMENT", "rejected");

        verify(paymentEventProducer).publishPaymentFailed(eq(1L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().itemName()).isNull();
    }

    @Test
    @DisplayName("재시도 작업 엔티티 - retryCount가 maxRetryCount보다 작으면 재시도 가능")
    void paymentRetryJobCanRetryTrueTest() {
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.SCHEDULED, LocalDateTime.now(), 2);

        assertThat(retryJob.canRetry()).isTrue();
    }

    @Test
    @DisplayName("재시도 작업 엔티티 - retryCount가 maxRetryCount에 도달하면 재시도 불가")
    void paymentRetryJobCanRetryFalseTest() {
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.SCHEDULED, LocalDateTime.now(), 3);

        assertThat(retryJob.canRetry()).isFalse();
    }

    @Test
    @DisplayName("재시도 작업 엔티티 - 실패 메시지가 null이면 null로 유지")
    void paymentRetryJobNullErrorMessageTest() {
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 1);

        retryJob.fail("ERROR", null);

        assertThat(retryJob.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("재시도 작업 엔티티 - 실패 메시지가 500자를 넘으면 잘라서 저장")
    void paymentRetryJobLongErrorMessageTruncateTest() {
        PaymentRetryJob retryJob = retryJob(100L, PaymentRetryJobStatus.RUNNING, LocalDateTime.now(), 1);
        String longMessage = "x".repeat(501);

        retryJob.fail("ERROR", longMessage);

        assertThat(retryJob.getLastErrorMessage()).hasSize(500);
    }

    @Test
    @DisplayName("customerKey 조회 - 저장된 키가 없으면 예외")
    void getCustomerKeyMissingForDirectLookupTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(
                repository,
                mock(TossCustomerKeyGenerator.class),
                realCustomerKeyEncryptor(),
                realCustomerKeyHashEncoder()
        );
        when(repository.findByMemberId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerKey(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.CUSTOMER_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("customerKey 조회 - 저장된 암호화 키를 복호화")
    void getCustomerKeyExistingTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        CustomerKeyEncryptor encryptor = realCustomerKeyEncryptor();
        CustomerKeyHashEncoder hashEncoder = realCustomerKeyHashEncoder();
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(
                repository,
                mock(TossCustomerKeyGenerator.class),
                encryptor,
                hashEncoder
        );
        PaymentCustomerKey customerKey = PaymentCustomerKey.create(
                1L,
                encryptor.encrypt("customer-key"),
                hashEncoder.encode("customer-key")
        );
        when(repository.findByMemberId(1L)).thenReturn(Optional.of(customerKey));

        String result = service.getCustomerKey(1L);

        assertThat(result).isEqualTo("customer-key");
    }

    @Test
    @DisplayName("customerKey 삭제 - memberId로 삭제 요청")
    void deleteCustomerKeyTest() {
        PaymentCustomerKeyRepository repository = mock(PaymentCustomerKeyRepository.class);
        PaymentCustomerKeyService service = new PaymentCustomerKeyService(
                repository,
                mock(TossCustomerKeyGenerator.class),
                realCustomerKeyEncryptor(),
                realCustomerKeyHashEncoder()
        );

        service.deleteCustomerKey(1L);

        verify(repository).deleteByMemberId(1L);
    }

    @Test
    @DisplayName("customerKey 암호화 도구 - 잘못된 키 길이면 예외")
    void customerKeyEncryptorInvalidSecretTest() {
        assertThatThrownBy(() -> new CustomerKeyEncryptor("c2hvcnQ="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("customerKey 암호화 도구 - 암호화 실패시 IllegalStateException")
    void customerKeyEncryptorEncryptFailureTest() {
        CustomerKeyEncryptor encryptor = realCustomerKeyEncryptor();

        assertThatThrownBy(() -> encryptor.encrypt(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("암호화");
    }

    @Test
    @DisplayName("customerKey 암호화 도구 - 복호화 실패시 IllegalStateException")
    void customerKeyEncryptorDecryptFailureTest() {
        CustomerKeyEncryptor encryptor = realCustomerKeyEncryptor();

        assertThatThrownBy(() -> encryptor.decrypt("invalid-encrypted-text"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화");
    }

    @Test
    @DisplayName("customerKey 해시 도구 - 해시 생성 실패시 IllegalStateException")
    void customerKeyHashEncoderFailureTest() {
        CustomerKeyHashEncoder hashEncoder = realCustomerKeyHashEncoder();

        assertThatThrownBy(() -> hashEncoder.encode(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("해시");
    }

    @Test
    @DisplayName("Toss 결제 예외 - 메시지가 없으면 기본 결제 실패 메시지 사용")
    void tossPaymentExceptionDefaultMessageTest() {
        TossPaymentException exception = new TossPaymentException(
                HttpStatus.BAD_REQUEST,
                "UNKNOWN",
                " "
        );

        assertThat(exception.getMessage())
                .isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED.getMessage());
    }

    @Test
    @DisplayName("Toss 결제 예외 - 메시지가 null이면 기본 결제 실패 메시지 사용")
    void tossPaymentExceptionNullMessageTest() {
        TossPaymentException exception = new TossPaymentException(
                HttpStatus.BAD_REQUEST,
                "UNKNOWN",
                null
        );

        assertThat(exception.getMessage())
                .isEqualTo(PaymentErrorCode.BILLING_PAYMENT_FAILED.getMessage());
    }

    private PaymentCustomerKeyService transactionCustomerKeyService;
    private BillingRepository transactionBillingRepository;
    private OrderRepository transactionOrderRepository;
    private PaymentRepository transactionPaymentRepository;
    private SubscriptionService transactionSubscriptionService;
    private PaymentEventProducer transactionEventProducer;

    private BillingPaymentTransactionService transactionService() {
        transactionCustomerKeyService = mock(PaymentCustomerKeyService.class);
        transactionBillingRepository = mock(BillingRepository.class);
        transactionOrderRepository = mock(OrderRepository.class);
        transactionPaymentRepository = mock(PaymentRepository.class);
        transactionSubscriptionService = mock(SubscriptionService.class);
        transactionEventProducer = mock(PaymentEventProducer.class);
        return new BillingPaymentTransactionService(
                transactionCustomerKeyService,
                transactionBillingRepository,
                transactionOrderRepository,
                transactionPaymentRepository,
                transactionSubscriptionService,
                transactionEventProducer
        );
    }

    private PaymentRetryJobTransactionService retryJobTransactionService(
            PaymentRetryJobRepository retryJobRepository,
            OrderRepository orderRepository,
            SubscriptionsRepository subscriptionsRepository,
            PaymentEventProducer paymentEventProducer
    ) {
        return retryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                mock(SubscriptionScheduledChangesRepository.class),
                paymentEventProducer
        );
    }

    private PaymentRetryJobTransactionService retryJobTransactionService(
            PaymentRetryJobRepository retryJobRepository,
            OrderRepository orderRepository,
            SubscriptionsRepository subscriptionsRepository,
            SubscriptionScheduledChangesRepository scheduledChangesRepository,
            PaymentEventProducer paymentEventProducer
    ) {
        return new PaymentRetryJobTransactionService(
                retryJobRepository,
                orderRepository,
                subscriptionsRepository,
                scheduledChangesRepository,
                paymentEventProducer
        );
    }

    private PaymentRetryJobTransactionService.PaymentRetryJobSnapshot retrySnapshot(
            Integer retryCount,
            Integer maxRetryCount
    ) {
        return new PaymentRetryJobTransactionService.PaymentRetryJobSnapshot(
                100L,
                1L,
                ORDER_NO,
                10L,
                retryCount,
                maxRetryCount
        );
    }

    private PaymentRetryJob retryJob(
            Long id,
            PaymentRetryJobStatus status,
            LocalDateTime nextRetryAt,
            Integer retryCount
    ) {
        return PaymentRetryJob.builder()
                .id(id)
                .memberId(1L)
                .orderNo(ORDER_NO)
                .billingId(10L)
                .retryCount(retryCount)
                .maxRetryCount(3)
                .nextRetryAt(nextRetryAt)
                .status(status)
                .lastErrorCode("TOSS_CONNECT_TIMEOUT")
                .lastErrorMessage("timeout")
                .build();
    }

    private BillingPaymentTransactionService.BillingPaymentPreparation transactionPreparation() {
        return new BillingPaymentTransactionService.BillingPaymentPreparation(
                1L,
                ORDER_NO,
                19900L,
                "Plus Subscription",
                10L,
                "billing-key",
                "customer-key",
                "billing-payment-" + ORDER_NO
        );
    }

    private Orders pendingBillingOrder() {
        Orders order = Orders.builder()
                .id(1L)
                .memberId(1L)
                .workspaceId(1L)
                .orderNo(ORDER_NO)
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.Billing)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();
        order.addOrderItem(OrderItems.builder()
                .productId(2L)
                .itemName("Plus Subscription")
                .itemType(ProductType.SUBSCRIPTION.name())
                .unitPrice(19900L)
                .quantity(1L)
                .totalPrice(19900L)
                .build());
        return order;
    }

    private Orders processingBillingOrder() {
        Orders order = pendingBillingOrder();
        order.markProcessing();
        return order;
    }

    private Subscriptions activeSubscription() {
        return Subscriptions.builder()
                .id(1L)
                .subscriptionPlanId(2L)
                .workspaceId(1L)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(30))
                .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                .billingId(10L)
                .build();
    }

    private Billing activeBilling() {
        return Billing.builder()
                .id(10L)
                .memberId(1L)
                .billingKey("billing-key")
                .billingStatus(BillingStatus.ACTIVE)
                .isDefault(true)
                .build();
    }

    private TossBillingKeyIssueResponse tossBillingKeyIssueResponse() {
        return new TossBillingKeyIssueResponse(
                "billing-key",
                "customer-key",
                "카드",
                new TossBillingKeyIssueResponse.CardInfo(
                        "41",
                        "123456******7890",
                        "신용",
                        "개인"
                )
        );
    }

    private CustomerKeyEncryptor realCustomerKeyEncryptor() {
        return new CustomerKeyEncryptor("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    private CustomerKeyHashEncoder realCustomerKeyHashEncoder() {
        return new CustomerKeyHashEncoder("test-customer-key-hash-secret");
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
        return response("DONE", 19900L);
    }

    private TossBillingPaymentResponse response(String status, Long totalAmount) {
        return new TossBillingPaymentResponse(
                "payment-key",
                ORDER_NO,
                "Plus Subscription",
                status,
                totalAmount,
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
