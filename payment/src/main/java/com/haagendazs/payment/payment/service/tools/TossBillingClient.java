package com.haagendazs.payment.payment.service.tools;

import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueResponse;
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
}
