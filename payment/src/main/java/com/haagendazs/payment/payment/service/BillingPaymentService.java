package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.PaymentNotificationPayload;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.PaymentMethod;
import com.haagendazs.payment.payment.enums.PaymentProvider;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.repository.PaymentRepository;
import com.haagendazs.payment.payment.service.dto.BillingPaymentRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingPaymentService {

    private final PaymentCustomerKeyService paymentCustomerKeyService;
    private final BillingRepository billingRepository;
    private final TossBillingClient tossBillingClient;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public void payWithRegisteredBillingMethod(Long memberId, BillingPaymentRequest request) {
        Orders order = null;

        try {
            order = getValidBillingOrder(memberId, request.orderNo());
            Billing billing = getDefaultBilling(memberId);
            String customerKey = paymentCustomerKeyService.getCustomerKey(memberId);

            TossBillingPaymentResponse paymentResponse =
                    executeBillingPayment(order, billing, customerKey);

            Payments payment = savePayment(order, paymentResponse);

            if (payment.getPaymentStatus() != PaymentStatus.DONE) {
                throw new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
            }

            order.complete();
            subscriptionService.activateSubscriptionByPayment(order, billing, payment);
            publishPaymentCompleted(memberId, order, payment);
        } catch (RuntimeException e) {
            publishPaymentFailed(memberId, request.orderNo(), order, e);
            throw e;
        }
    }

    private Orders getValidBillingOrder(Long memberId, String orderNo) {
        Orders order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(PaymentErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getOrderStatus() != OrderStatus.PENDING) {
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

    private Billing getDefaultBilling(Long memberId) {
        return billingRepository.findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
                        memberId,
                        BillingStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_METHOD_NOT_FOUND));
    }

    private TossBillingPaymentResponse executeBillingPayment(
            Orders order,
            Billing billing,
            String customerKey
    ) {
        TossBillingPaymentResponse paymentResponse =
                tossBillingClient.payWithBillingKey(
                        billing.getBillingKey(),
                        customerKey,
                        order.getOrderNo(),
                        order.getTotalAmount(),
                        order.getOrderItems().get(0).getItemName()
                );

        if (!PaymentStatus.DONE.name().equals(paymentResponse.status())
                || !order.getTotalAmount().equals(paymentResponse.totalAmount())) {
            throw new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
        }

        return paymentResponse;
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
}
