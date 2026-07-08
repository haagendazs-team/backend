package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.CardType;
import com.haagendazs.payment.payment.enums.OwnerType;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.service.dto.BillingKeyIssueRequest;
import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentCustomerKeyService paymentCustomerKeyService;
    private final BillingRepository billingRepository;
    private final TossBillingClient tossBillingClient;

    @Transactional
    public void billingKeyIssue(Long memberId, BillingKeyIssueRequest request) {

        String savedCustomerKey = paymentCustomerKeyService.getCustomerKey(memberId);

        if (!savedCustomerKey.equals(request.customerKey())) {
            throw new BusinessException(PaymentErrorCode.INVALID_CUSTOMER_KEY);
        }

        TossBillingKeyIssueResponse tossResponse =
                tossBillingClient.issueBillingKey(
                        request.authKey(),
                        request.customerKey()
                );

        Billing billing = Billing.builder()
                .memberId(memberId)
                .billingKey(tossResponse.billingKey())
                .issuerCode(CardCompany.fromCode(tossResponse.card().issuerCode()))
                .cardNumber(tossResponse.card().number())
                .cardType(CardType.from(tossResponse.card().cardType()))
                .ownerType(OwnerType.from(tossResponse.card().ownerType()))
                .billingStatus(BillingStatus.ACTIVE)
                .build();

        billingRepository.save(billing);
    }
}
