package com.haagendazs.payment.payment.service;

import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.service.dto.BillingMethodIssueAndPayRequest;
import com.haagendazs.payment.payment.service.dto.BillingMethodIssueRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingCheckoutService {

    private final BillingMethodService billingMethodService;
    private final BillingPaymentService billingPaymentService;

    // Toss billing auth 성공 후 받은 authKey로 결제수단을 등록하고, 방금 등록한 수단으로 주문을 결제합니다.
    public void issueBillingMethodAndPay(
            Long memberId,
            BillingMethodIssueAndPayRequest request
    ) {
        Billing billing = billingMethodService.issueBillingMethod(
                memberId,
                new BillingMethodIssueRequest(request.authKey())
        );

        billingPaymentService.payWithBillingMethod(memberId, request.orderNo(), billing);
    }
}
