package com.haagendazs.application.service;

import com.haagendazs.domain.model.Billing;
import com.haagendazs.application.dto.BillingMethodIssueAndPayRequest;
import com.haagendazs.application.dto.BillingMethodIssueRequest;
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

        billingPaymentService.payCheckoutWithBillingMethod(memberId, request.orderNo(), billing);
    }
}
