package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.PaymentStatus;
import com.haagendazs.payment.payment.service.dto.BillingPaymentRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
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
        payWithBillingMethod(memberId, request.orderNo(), null);
    }

    // 지정된 결제수단으로 주문을 결제합니다. 결제수단이 null이면 기본 결제수단을 사용합니다.
    public void payWithBillingMethod(Long memberId, String orderNo, Billing billing) {
        BillingPaymentTransactionService.BillingPaymentPreparation preparation =
                billingPaymentTransactionService.prepareBillingPayment(memberId, orderNo, billing);

        TossBillingPaymentResponse paymentResponse;

        try {
            paymentResponse = executeBillingPayment(preparation);
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
            billingPaymentTransactionService.completeBillingPayment(memberId, preparation, paymentResponse);
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

    private boolean isSuccessfulPayment(
            BillingPaymentTransactionService.BillingPaymentPreparation preparation,
            TossBillingPaymentResponse paymentResponse
    ) {
        return PaymentStatus.DONE.name().equals(paymentResponse.status())
                && preparation.amount().equals(paymentResponse.totalAmount());
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
}
