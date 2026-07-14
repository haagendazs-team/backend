package com.haagendazs.payment.payment.service.tools;

import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueResponse;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class TossBillingClient {

    private final RestClient restClient;

    public TossBillingKeyIssueResponse issueBillingKey(
            String authKey,
            String customerKey
    ) {
        TossBillingKeyIssueRequest request = new TossBillingKeyIssueRequest(
                authKey,
                customerKey
        );

        return restClient.post()
                .uri("/v1/billing/authorizations/issue")
                .body(request)
                .retrieve()
                .body(TossBillingKeyIssueResponse.class);
    }

    public TossBillingPaymentResponse payWithBillingKey(
            String billingKey,
            String customerKey,
            String orderId, //주문번호
            Long amount,
            String orderName,
            String idempotencyKey
    ) {
        TossBillingPaymentRequest request = new TossBillingPaymentRequest(
                customerKey,
                orderId,
                amount,
                orderName
        );

        return restClient.post()
                .uri("/v1/billing/{billingKey}", billingKey)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(TossBillingPaymentResponse.class);
    }

    public void deleteBillingKey(String billingKey) {
        restClient.delete()
                .uri("/v1/billing/{billingKey}", billingKey)
                .retrieve()
                .toBodilessEntity();
    }
}
