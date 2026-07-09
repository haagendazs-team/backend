package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.entity.Payments;
import com.haagendazs.payment.payment.enums.*;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.repository.PaymentRepository;
import com.haagendazs.payment.payment.service.dto.BillingKeyIssueRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueResponse;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;

import com.haagendazs.payment.subscription.service.SubscriptionService;

import java.time.LocalDateTime;

import java.time.OffsetDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentCustomerKeyService paymentCustomerKeyService;
    private final BillingRepository billingRepository;
    private final TossBillingClient tossBillingClient;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public void issueBillingAndPay(Long memberId, BillingKeyIssueRequest request) {

        //결제 가져오기
        Orders order = getValidBillingOrder(memberId, request.orderNo());

        //소비자 키 검증
        String customerKey = validateCustomerKey(memberId, request.customerKey());

        TossBillingKeyIssueResponse tossResponse =
                tossBillingClient.issueBillingKey(
                        request.authKey(),
                        customerKey
                );

        Billing billing = saveBilling(memberId, tossResponse);

        TossBillingPaymentResponse paymentResponse =
                executeInitialBillingPayment(order, billing, customerKey);

        Payments payment = savePayment(order, paymentResponse);

        if (payment.getPaymentStatus() != PaymentStatus.DONE) {
            throw new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
        }

        order.complete();

        //구독플랜적용
        subscriptionService.activateSubscriptionByPayment(
                order,
                billing,
                payment
        );
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

    private String validateCustomerKey(Long memberId, String requestCustomerKey) {
        String savedCustomerKey = paymentCustomerKeyService.getCustomerKey(memberId);

        if (!savedCustomerKey.equals(requestCustomerKey)) {
            throw new BusinessException(PaymentErrorCode.INVALID_CUSTOMER_KEY);
        }

        return savedCustomerKey;
    }

    private Billing saveBilling(
            Long memberId,
            TossBillingKeyIssueResponse tossResponse
    ) {

        Billing billing = Billing.builder()
                .memberId(memberId)
                .billingKey(tossResponse.billingKey())
                .issuerCode(CardCompany.fromCode(tossResponse.card().issuerCode()))
                .cardNumber(tossResponse.card().number())
                .cardType(CardType.from(tossResponse.card().cardType()))
                .ownerType(OwnerType.from(tossResponse.card().ownerType()))
                .isDefault(true)
                .billingStatus(BillingStatus.ACTIVE)
                .build();

        return billingRepository.save(billing);
    }

    private TossBillingPaymentResponse executeInitialBillingPayment(
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

        if (!"DONE".equals(paymentResponse.status())) {
            throw new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
        }

        return paymentResponse;
    }

    private Payments savePayment(
            Orders order,
            TossBillingPaymentResponse response
    ) {
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

    private LocalDateTime parseDateTime(String time){
        OffsetDateTime odt = OffsetDateTime.parse(time);

        LocalDateTime ldt = odt.toLocalDateTime();
        return ldt;
    }
}
