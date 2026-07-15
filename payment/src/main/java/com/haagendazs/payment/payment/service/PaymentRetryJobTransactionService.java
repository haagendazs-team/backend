package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.PaymentEventProducer;
import com.haagendazs.payment.global.kafka.dto.PaymentNotificationPayload;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.payment.entity.PaymentRetryJob;
import com.haagendazs.payment.payment.enums.PaymentRetryJobStatus;
import com.haagendazs.payment.payment.repository.PaymentRetryJobRepository;
import com.haagendazs.payment.product.entity.OrderItems;
import com.haagendazs.payment.subscription.entity.Subscriptions;
import com.haagendazs.payment.subscription.repository.SubscriptionsRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRetryJobTransactionService {

    private final PaymentRetryJobRepository paymentRetryJobRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final PaymentEventProducer paymentEventProducer;

    // 정기 자동결제 실패 후 재시도 작업을 예약합니다.
    @Transactional
    public PaymentRetryJob scheduleRetry(
            Long memberId,
            String orderNo,
            Long billingId,
            int maxRetryCount,
            LocalDateTime nextRetryAt,
            String lastErrorCode,
            String lastErrorMessage
    ) {
        Orders order = getOrder(memberId, orderNo);
        order.markRetryScheduled();
        markSubscriptionPastDue(order);

        PaymentRetryJob retryJob = PaymentRetryJob.builder()
                .memberId(memberId)
                .orderNo(orderNo)
                .billingId(billingId)
                .retryCount(0)
                .maxRetryCount(maxRetryCount)
                .nextRetryAt(nextRetryAt)
                .status(PaymentRetryJobStatus.SCHEDULED)
                .lastErrorCode(lastErrorCode)
                .lastErrorMessage(lastErrorMessage)
                .build();

        return paymentRetryJobRepository.save(retryJob);
    }

    // 실행 시간이 된 재시도 작업 ID만 조회합니다. 실제 처리는 작업별 트랜잭션에서 수행합니다.
    @Transactional(readOnly = true)
    public List<Long> findDueRetryJobIds(LocalDateTime now, int batchSize) {
        return paymentRetryJobRepository
                .findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        PaymentRetryJobStatus.SCHEDULED,
                        now,
                        PageRequest.of(0, batchSize)
                )
                .stream()
                .map(PaymentRetryJob::getId)
                .toList();
    }

    // 재시도 작업을 실행 중으로 전환하고, PG 재시도에 필요한 값을 확정합니다.
    @Transactional
    public PaymentRetryJobSnapshot startRetryJob(Long retryJobId, LocalDateTime now) {
        PaymentRetryJob retryJob = getRetryJob(retryJobId);

        if (retryJob.getStatus() != PaymentRetryJobStatus.SCHEDULED
                || retryJob.getNextRetryAt().isAfter(now)) {
            throw new BusinessException(PaymentErrorCode.INVALID_ORDER_STATUS);
        }

        retryJob.markRunning(now);

        return new PaymentRetryJobSnapshot(
                retryJob.getId(),
                retryJob.getMemberId(),
                retryJob.getOrderNo(),
                retryJob.getBillingId(),
                retryJob.getRetryCount(),
                retryJob.getMaxRetryCount()
        );
    }

    @Transactional
    public void succeedRetryJob(Long retryJobId) {
        getRetryJob(retryJobId).succeed();
    }

    @Transactional
    public void rescheduleRetryJob(
            Long retryJobId,
            LocalDateTime nextRetryAt,
            String lastErrorCode,
            String lastErrorMessage
    ) {
        PaymentRetryJob retryJob = getRetryJob(retryJobId);
        Orders order = getOrder(retryJob.getMemberId(), retryJob.getOrderNo());

        retryJob.reschedule(nextRetryAt, lastErrorCode, lastErrorMessage);
        order.markRetryScheduled();
        markSubscriptionPastDue(order);
    }

    @Transactional
    public void failRetryJob(
            Long retryJobId,
            String lastErrorCode,
            String lastErrorMessage
    ) {
        PaymentRetryJob retryJob = getRetryJob(retryJobId);
        Orders order = getOrder(retryJob.getMemberId(), retryJob.getOrderNo());

        retryJob.fail(lastErrorCode, lastErrorMessage);
        order.fail();
        expireSubscription(order);
        publishFinalPaymentFailed(retryJob.getMemberId(), order, lastErrorCode, lastErrorMessage);
    }

    private PaymentRetryJob getRetryJob(Long retryJobId) {
        return paymentRetryJobRepository.findById(retryJobId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));
    }

    private Orders getOrder(Long memberId, String orderNo) {
        Orders order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(PaymentErrorCode.ORDER_ACCESS_DENIED);
        }

        return order;
    }

    private void markSubscriptionPastDue(Orders order) {
        getSubscription(order).markPastDue();
    }

    private void expireSubscription(Orders order) {
        getSubscription(order).expire();
    }

    private Subscriptions getSubscription(Orders order) {
        return subscriptionsRepository.findByWorkspaceId(order.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    private void publishFinalPaymentFailed(
            Long memberId,
            Orders order,
            String lastErrorCode,
            String lastErrorMessage
    ) {
        try {
            paymentEventProducer.publishPaymentFailed(
                    memberId,
                    new PaymentNotificationPayload(
                            "FAILED",
                            order.getOrderNo(),
                            order.getId(),
                            order.getWorkspaceId(),
                            order.getTotalAmount(),
                            getFirstItemName(order),
                            null,
                            lastErrorCode,
                            lastErrorMessage,
                            LocalDateTime.now()
                    )
            );
        } catch (RuntimeException e) {
            log.warn("정기 자동결제 최종 실패 알림 Kafka 발행 실패 memberId={} orderNo={}",
                    memberId,
                    order.getOrderNo(),
                    e
            );
        }
    }

    private String getFirstItemName(Orders order) {
        if (order.getOrderItems().isEmpty()) {
            return null;
        }

        return order.getOrderItems().stream()
                .findFirst()
                .map(OrderItems::getItemName)
                .orElse(null);
    }

    public record PaymentRetryJobSnapshot(
            Long retryJobId,
            Long memberId,
            String orderNo,
            Long billingId,
            Integer retryCount,
            Integer maxRetryCount
    ) {
    }
}
