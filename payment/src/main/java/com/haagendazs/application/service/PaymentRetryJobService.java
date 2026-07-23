package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.domain.model.Billing;
import com.haagendazs.domain.model.BillingStatus;
import com.haagendazs.domain.model.TossPaymentErrorAction;
import com.haagendazs.domain.repository.BillingRepository;
import com.haagendazs.infrastructure.toss.TossPaymentException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRetryJobService {

    private static final int BATCH_SIZE = 50;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            Duration.ofHours(24)
    );

    private final PaymentRetryJobTransactionService paymentRetryJobTransactionService;
    private final BillingPaymentService billingPaymentService;
    private final BillingRepository billingRepository;

    public void scheduleRetry(
            Long memberId,
            String orderNo,
            Long billingId,
            RuntimeException exception
    ) {
        paymentRetryJobTransactionService.scheduleRetry(
                memberId,
                orderNo,
                billingId,
                RETRY_DELAYS.size(),
                LocalDateTime.now().plus(RETRY_DELAYS.get(0)),
                getFailCode(exception),
                getFailMessage(exception)
        );
    }

    public void processDueRetryJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> retryJobIds = paymentRetryJobTransactionService.findDueRetryJobIds(now, BATCH_SIZE);

        retryJobIds.forEach(this::processRetryJob);
    }

    private void processRetryJob(Long retryJobId) {
        PaymentRetryJobTransactionService.PaymentRetryJobSnapshot retryJob =
                paymentRetryJobTransactionService.startRetryJob(retryJobId, LocalDateTime.now());

        try {
            Billing billing = getActiveBilling(retryJob.memberId(), retryJob.billingId());
            billingPaymentService.paySubscriptionRenewalWithBillingMethod(
                    retryJob.memberId(),
                    retryJob.orderNo(),
                    billing
            );
            paymentRetryJobTransactionService.succeedRetryJob(retryJob.retryJobId());
        } catch (RuntimeException e) {
            handleRetryFailure(retryJob, e);
        }
    }

    private void handleRetryFailure(
            PaymentRetryJobTransactionService.PaymentRetryJobSnapshot retryJob,
            RuntimeException exception
    ) {
        if (shouldRetry(exception, retryJob)) {
            paymentRetryJobTransactionService.rescheduleRetryJob(
                    retryJob.retryJobId(),
                    LocalDateTime.now().plus(nextDelay(retryJob.retryCount() + 1)),
                    getFailCode(exception),
                    getFailMessage(exception)
            );
            return;
        }

        paymentRetryJobTransactionService.failRetryJob(
                retryJob.retryJobId(),
                getFailCode(exception),
                getFailMessage(exception)
        );
    }

    private boolean shouldRetry(
            RuntimeException exception,
            PaymentRetryJobTransactionService.PaymentRetryJobSnapshot retryJob
    ) {
        return retryJob.retryCount() + 1 < retryJob.maxRetryCount()
                && exception instanceof TossPaymentException tossPaymentException
                && tossPaymentException.getTossPaymentErrorCode().getAction() == TossPaymentErrorAction.RETRY_LATER;
    }

    private Duration nextDelay(Integer retryCount) {
        int index = Math.min(retryCount, RETRY_DELAYS.size() - 1);

        return RETRY_DELAYS.get(index);
    }

    private Billing getActiveBilling(Long memberId, Long billingId) {
        return billingRepository.findByIdAndMemberIdAndBillingStatus(
                        billingId,
                        memberId,
                        BillingStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_METHOD_NOT_FOUND));
    }

    private String getFailCode(RuntimeException exception) {
        if (exception instanceof TossPaymentException tossPaymentException) {
            return tossPaymentException.getTossCode();
        }
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getCode();
        }
        return exception.getClass().getSimpleName();
    }

    private String getFailMessage(RuntimeException exception) {
        if (exception instanceof TossPaymentException tossPaymentException
                && tossPaymentException.getTossMessage() != null
                && !tossPaymentException.getTossMessage().isBlank()) {
            return tossPaymentException.getTossMessage();
        }
        return exception.getMessage();
    }
}
