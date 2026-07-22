package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.infrastructure.kafka.PaymentEventProducer;
import com.haagendazs.infrastructure.kafka.dto.PaymentNotificationPayload;
import com.haagendazs.domain.model.Orders;
import com.haagendazs.domain.model.OrderStatus;
import com.haagendazs.domain.model.OrderType;
import com.haagendazs.domain.repository.OrderRepository;
import com.haagendazs.domain.model.Billing;
import com.haagendazs.domain.model.Payments;
import com.haagendazs.domain.model.BillingStatus;
import com.haagendazs.domain.model.CardCompany;
import com.haagendazs.domain.model.PaymentMethod;
import com.haagendazs.domain.model.PaymentProvider;
import com.haagendazs.domain.model.PaymentStatus;
import com.haagendazs.domain.repository.BillingRepository;
import com.haagendazs.domain.repository.PaymentRepository;
import com.haagendazs.application.dto.TossBillingPaymentResponse;
import com.haagendazs.application.service.SubscriptionService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingPaymentTransactionService {

    private static final String BILLING_PAYMENT_IDEMPOTENCY_KEY_PREFIX = "billing-payment-";

    private final PaymentCustomerKeyService paymentCustomerKeyService;
    private final BillingRepository billingRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final PaymentEventProducer paymentEventProducer;

    // 주문을 결제 처리 중 상태로 전환하고, PG 요청에 필요한 값을 트랜잭션 안에서 확정합니다.
    @Transactional
    public BillingPaymentPreparation prepareBillingPayment(
            Long memberId,
            String orderNo,
            Billing requestedBilling
    ) {
        Orders order = getValidBillingOrder(memberId, orderNo);
        Billing billing = resolveBilling(memberId, requestedBilling);
        String customerKey = paymentCustomerKeyService.getCustomerKey(memberId);

        order.markProcessing();

        return new BillingPaymentPreparation(
                order.getId(),
                order.getOrderNo(),
                order.getTotalAmount(),
                getFirstItemName(order),
                billing.getId(),
                billing.getBillingKey(),
                customerKey,
                createBillingPaymentIdempotencyKey(order)
        );
    }

    // PG 결제 성공 응답을 로컬 결제/주문/구독 상태에 반영합니다.
    @Transactional
    public void completeBillingPayment(
            Long memberId,
            BillingPaymentPreparation preparation,
            TossBillingPaymentResponse response
    ) {
        Orders order = getProcessingOrder(memberId, preparation.orderNo());
        Billing billing = getActiveBilling(memberId, preparation.billingId());
        Payments payment = savePayment(order, response);

        order.complete();
        subscriptionService.activateSubscriptionByPayment(order, billing);
        publishPaymentCompleted(memberId, order, payment);
    }

    // 정기 자동결제 성공 응답을 로컬 결제/주문/구독 갱신 상태에 반영합니다.
    @Transactional
    public void completeSubscriptionRenewalPayment(
            Long memberId,
            BillingPaymentPreparation preparation,
            TossBillingPaymentResponse response
    ) {
        Orders order = getProcessingOrder(memberId, preparation.orderNo());
        Billing billing = getActiveBilling(memberId, preparation.billingId());
        Payments payment = savePayment(order, response);

        order.complete();
        subscriptionService.renewSubscriptionByPayment(order, billing);
        publishPaymentCompleted(memberId, order, payment);
    }

    // PG 결제 실패 또는 PG 호출 실패를 로컬 주문 실패 상태로 반영합니다.
    @Transactional
    public void failBillingPayment(
            Long memberId,
            BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        Orders order = getProcessingOrder(memberId, preparation.orderNo());

        order.fail();
        publishPaymentFailed(memberId, preparation.orderNo(), order, exception);
    }

    // 카드 유효기간 만료로 자동결제가 실패하면 주문과 결제수단을 함께 사용 불가 상태로 전환합니다.
    @Transactional
    public void failBillingPaymentAndExpireBillingMethod(
            Long memberId,
            BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        Orders order = getProcessingOrder(memberId, preparation.orderNo());
        Billing billing = getActiveBilling(memberId, preparation.billingId());

        order.fail();
        billing.expire();
        publishPaymentFailed(memberId, preparation.orderNo(), order, exception);
    }

    // 정기 자동결제 일시 실패를 재시도 예약 상태로 남깁니다.
    @Transactional
    public void markRetryScheduled(
            Long memberId,
            BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        Orders order = getProcessingOrder(memberId, preparation.orderNo());

        order.markRetryScheduled();
        publishPaymentFailed(memberId, preparation.orderNo(), order, exception);
    }

    // PG 결제는 성공했지만 로컬 후처리가 실패한 경우, 수동/배치 보정 대상으로 남깁니다.
    @Transactional
    public void markReconcileRequired(
            Long memberId,
            BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        Orders order = getProcessingOrder(memberId, preparation.orderNo());

        order.markReconcileRequired();
        publishPaymentFailed(memberId, preparation.orderNo(), order, exception);
    }

    private Orders getValidBillingOrder(Long memberId, String orderNo) {
        Orders order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(PaymentErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getOrderStatus() != OrderStatus.PENDING
                && order.getOrderStatus() != OrderStatus.RETRY_SCHEDULED) {
            throw new BusinessException(PaymentErrorCode.INVALID_ORDER_STATUS);
        }
        if (order.getOrderType() != OrderType.Billing) {
            throw new BusinessException(PaymentErrorCode.INVALID_ORDER_TYPE);
        }
        if (order.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.ORDER_EXPIRED);
        }

        return order;
    }

    private Orders getProcessingOrder(Long memberId, String orderNo) {
        Orders order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(PaymentErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getOrderStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException(PaymentErrorCode.INVALID_ORDER_STATUS);
        }

        return order;
    }

    private Billing resolveBilling(Long memberId, Billing requestedBilling) {
        if (requestedBilling == null) {
            return billingRepository.findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                            memberId,
                            BillingStatus.ACTIVE
                    )
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_METHOD_NOT_FOUND));
        }

        return getActiveBilling(memberId, requestedBilling.getId());
    }

    private Billing getActiveBilling(Long memberId, Long billingId) {
        return billingRepository.findByIdAndMemberIdAndBillingStatus(
                        billingId,
                        memberId,
                        BillingStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_METHOD_NOT_FOUND));
    }

    private String createBillingPaymentIdempotencyKey(Orders order) {
        return BILLING_PAYMENT_IDEMPOTENCY_KEY_PREFIX + order.getOrderNo();
    }

    private Payments savePayment(Orders order, TossBillingPaymentResponse response) {
        Payments payment = Payments.builder()
                .orderId(order.getId())
                .paymentKey(response.paymentKey())
                .paymentMethod(PaymentMethod.CARD)
                .paymentProvider(PaymentProvider.TOSS)
                .paymentStatus(PaymentStatus.from(response.status()))
                .totalAmount(response.totalAmount())
                .approvedAt(parseDateTime(response.approvedAt()))
                .cardCompany(CardCompany.fromCode(response.card().issuerCode()))
                .cardNumber(response.card().number())
                .receiptUrl(response.receipt().url())
                .build();

        return paymentRepository.save(payment);
    }

    private LocalDateTime parseDateTime(String time) {
        OffsetDateTime odt = OffsetDateTime.parse(time);

        return odt.toLocalDateTime();
    }

    private void publishPaymentCompleted(Long memberId, Orders order, Payments payment) {
        try {
            paymentEventProducer.publishPaymentCompleted(
                    memberId,
                    new PaymentNotificationPayload(
                            payment.getPaymentStatus().name(),
                            order.getOrderNo(),
                            order.getId(),
                            order.getWorkspaceId(),
                            payment.getTotalAmount(),
                            getFirstItemName(order),
                            payment.getReceiptUrl(),
                            null,
                            null,
                            LocalDateTime.now()
                    )
            );
        } catch (RuntimeException e) {
            log.warn("결제 성공 알림 Kafka 발행 실패 memberId={} orderNo={}", memberId, order.getOrderNo(), e);
        }
    }

    private void publishPaymentFailed(
            Long memberId,
            String orderNo,
            Orders order,
            RuntimeException exception
    ) {
        try {
            paymentEventProducer.publishPaymentFailed(
                    memberId,
                    new PaymentNotificationPayload(
                            "FAILED",
                            orderNo,
                            order == null ? null : order.getId(),
                            order == null ? null : order.getWorkspaceId(),
                            order == null ? null : order.getTotalAmount(),
                            order == null ? null : getFirstItemName(order),
                            null,
                            getFailCode(exception),
                            exception.getMessage(),
                            LocalDateTime.now()
                    )
            );
        } catch (RuntimeException e) {
            log.warn("결제 실패 알림 Kafka 발행 실패 memberId={} orderNo={}", memberId, orderNo, e);
        }
    }

    private String getFirstItemName(Orders order) {
        if (order.getOrderItems().isEmpty()) {
            return null;
        }
        return order.getOrderItems().get(0).getItemName();
    }

    private String getFailCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getCode();
        }
        return exception.getClass().getSimpleName();
    }

    public record BillingPaymentPreparation(
            Long orderId,
            String orderNo,
            Long amount,
            String orderName,
            Long billingId,
            String billingKey,
            String customerKey,
            String idempotencyKey
    ) {
    }
}
