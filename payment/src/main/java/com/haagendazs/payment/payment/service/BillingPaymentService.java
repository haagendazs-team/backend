package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import com.haagendazs.payment.payment.enums.TossPaymentErrorAction;
import com.haagendazs.payment.payment.service.dto.BillingPaymentRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import com.haagendazs.payment.payment.service.tools.TossPaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingPaymentService {

    private final TossBillingClient tossBillingClient;
    private final BillingPaymentTransactionService billingPaymentTransactionService;

    // 회원의 기본 자동결제 결제수단으로 주문을 결제합니다.
    public void payWithRegisteredBillingMethod(Long memberId, BillingPaymentRequest request) {
        payCheckoutWithBillingMethod(memberId, request.orderNo(), null);
    }

    // checkout 결제입니다. 실패하면 즉시 실패 처리하고 사용자에게 피드백합니다.
    public void payCheckoutWithBillingMethod(Long memberId, String orderNo, Billing billing) {
        payWithBillingMethod(memberId, orderNo, billing, PaymentFailurePolicy.CHECKOUT);
    }

    // 정기 자동결제입니다. 일시 장애는 실패 확정 대신 재시도 예약 상태로 남깁니다.
    public void paySubscriptionRenewalWithBillingMethod(Long memberId, String orderNo, Billing billing) {
        payWithBillingMethod(memberId, orderNo, billing, PaymentFailurePolicy.SUBSCRIPTION_RENEWAL);
    }

    private void payWithBillingMethod(
            Long memberId,
            String orderNo,
            Billing billing,
            PaymentFailurePolicy failurePolicy
    ) {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation =
                billingPaymentTransactionService.prepareBillingPayment(memberId, orderNo, billing);

        TossBillingPaymentResponse paymentResponse;

        try {
            paymentResponse = executeBillingPayment(preparation);
        } catch (TossPaymentException e) {
            if (handleTossPaymentException(memberId, preparation, e, failurePolicy)) {
                return;
            }
            throw e;
        } catch (RuntimeException e) {
            failBillingPayment(memberId, preparation, e);
            throw e;
        }

        if (!isSuccessfulPayment(preparation, paymentResponse)) {
            BusinessException exception =
                    new BusinessException(PaymentErrorCode.BILLING_PAYMENT_FAILED);
            failBillingPayment(memberId, preparation, exception);
            throw exception;
        }

        try {
            completePayment(memberId, preparation, paymentResponse, failurePolicy);
        } catch (RuntimeException e) {
            markReconcileRequired(memberId, preparation, e);
            throw e;
        }
    }

    private TossBillingPaymentResponse executeBillingPayment(
            BillingPaymentTransactionService.BillingPaymentPreparation preparation
    ) {
        return tossBillingClient.payWithBillingKey(
                preparation.billingKey(),
                preparation.customerKey(),
                preparation.orderNo(),
                preparation.amount(),
                preparation.orderName(),
                preparation.idempotencyKey()
        );
    }

    private boolean handleTossPaymentException(
            Long memberId,
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            TossPaymentException exception,
            PaymentFailurePolicy failurePolicy
    ) {
        TossPaymentErrorAction action = exception.getTossPaymentErrorCode().getAction();

        if (action == TossPaymentErrorAction.QUERY_AND_RECONCILE) {
            return queryAndReconcile(memberId, preparation, exception, failurePolicy);
        }

        if (failurePolicy == PaymentFailurePolicy.SUBSCRIPTION_RENEWAL
                && action == TossPaymentErrorAction.RETRY_LATER) {
            markRetryScheduled(memberId, preparation, exception);
            return false;
        }

        failBillingPayment(memberId, preparation, exception);
        return false;
    }

    private boolean queryAndReconcile(
            Long memberId,
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            TossPaymentException exception,
            PaymentFailurePolicy failurePolicy
    ) {
        try {
            TossBillingPaymentResponse queriedPayment =
                    tossBillingClient.getPaymentByOrderId(preparation.orderNo());

            if (isSuccessfulPayment(preparation, queriedPayment)) {
                completePayment(
                        memberId,
                        preparation,
                        queriedPayment,
                        failurePolicy
                );
                return true;
            }

            failBillingPayment(memberId, preparation, exception);
        } catch (RuntimeException reconcileException) {
            markReconcileRequired(memberId, preparation, reconcileException);
        }

        return false;
    }

    private boolean isSuccessfulPayment(
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            TossBillingPaymentResponse paymentResponse
    ) {
        return PaymentStatus.DONE.name().equals(paymentResponse.status())
                && preparation.amount().equals(paymentResponse.totalAmount());
    }

    private void completePayment(
            Long memberId,
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            TossBillingPaymentResponse paymentResponse,
            PaymentFailurePolicy failurePolicy
    ) {
        if (failurePolicy == PaymentFailurePolicy.SUBSCRIPTION_RENEWAL) {
            billingPaymentTransactionService.completeSubscriptionRenewalPayment(
                    memberId,
                    preparation,
                    paymentResponse
            );
            return;
        }

        billingPaymentTransactionService.completeBillingPayment(memberId, preparation, paymentResponse);
    }

    private void failBillingPayment(
            Long memberId,
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        try {
            billingPaymentTransactionService.failBillingPayment(memberId, preparation, exception);
        } catch (RuntimeException handlingException) {
            log.warn(
                    "자동결제 실패 후 상태 보정 실패 memberId={} orderNo={}",
                    memberId,
                    preparation.orderNo(),
                    handlingException
            );
        }
    }

    private void markReconcileRequired(
            Long memberId,
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        try {
            billingPaymentTransactionService.markReconcileRequired(memberId, preparation, exception);
        } catch (RuntimeException handlingException) {
            log.warn(
                    "자동결제 성공 후 상태 보정 실패 memberId={} orderNo={}",
                    memberId,
                    preparation.orderNo(),
                    handlingException
            );
        }
    }

    private void markRetryScheduled(
            Long memberId,
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            RuntimeException exception
    ) {
        try {
            billingPaymentTransactionService.markRetryScheduled(memberId, preparation, exception);
        } catch (RuntimeException handlingException) {
            log.warn(
                    "자동결제 재시도 예약 상태 보정 실패 memberId={} orderNo={}",
                    memberId,
                    preparation.orderNo(),
                    handlingException
            );
        }
    }

    private enum PaymentFailurePolicy {
        CHECKOUT,
        SUBSCRIPTION_RENEWAL
    }
}
