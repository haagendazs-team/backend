package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.BillingStatus;
import com.haagendazs.payment.payment.enums.CardCompany;
import com.haagendazs.payment.payment.enums.CardType;
import com.haagendazs.payment.payment.enums.OwnerType;
import com.haagendazs.payment.payment.repository.BillingRepository;
import com.haagendazs.payment.payment.service.dto.BillingMethodIssueRequest;
import com.haagendazs.payment.payment.service.dto.BillingMethodPrepareResponse;
import com.haagendazs.payment.payment.service.dto.TossBillingKeyIssueResponse;
import com.haagendazs.payment.payment.service.tools.TossBillingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingMethodService {

    private static final int MAX_BILLING_METHOD_COUNT = 5;

    private final PaymentCustomerKeyService paymentCustomerKeyService;
    private final BillingRepository billingRepository;
    private final TossBillingClient tossBillingClient;

    @Transactional
    public BillingMethodPrepareResponse prepareRegistration(Long memberId) {
        String customerKey = paymentCustomerKeyService.getOrCreateCustomerKey(memberId);

        return new BillingMethodPrepareResponse(customerKey);
    }

    @Transactional
    public void issueBillingMethod(Long memberId, BillingMethodIssueRequest request) {
        validateBillingMethodLimit(memberId);
        String customerKey = validateCustomerKey(memberId, request.customerKey());

        TossBillingKeyIssueResponse tossResponse =
                tossBillingClient.issueBillingKey(
                        request.authKey(),
                        customerKey
                );

        saveBilling(memberId, tossResponse, shouldRegisterAsDefault(memberId));
    }

    @Transactional
    public void deleteBillingMethod(Long memberId, Long billingId) {
        Billing billing = billingRepository.findByIdAndMemberIdAndBillingStatus(
                        billingId,
                        memberId,
                        BillingStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.BILLING_METHOD_NOT_FOUND));

        deleteTossBillingKey(billing.getBillingKey());
        billing.deactivate();
        ensureSingleActiveBillingMethodIsDefault(memberId);
    }

    private void deleteTossBillingKey(String billingKey) {
        try {
            tossBillingClient.deleteBillingKey(billingKey);
        } catch (RestClientException e) {
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_DELETE_FAILED);
        }
    }

    private void validateBillingMethodLimit(Long memberId) {
        long activeBillingMethodCount =
                billingRepository.countByMemberIdAndBillingStatus(memberId, BillingStatus.ACTIVE);

        if (activeBillingMethodCount >= MAX_BILLING_METHOD_COUNT) {
            throw new BusinessException(PaymentErrorCode.BILLING_METHOD_LIMIT_EXCEEDED);
        }
    }

    private String validateCustomerKey(Long memberId, String requestCustomerKey) {
        String savedCustomerKey = paymentCustomerKeyService.getCustomerKey(memberId);

        if (!savedCustomerKey.equals(requestCustomerKey)) {
            throw new BusinessException(PaymentErrorCode.INVALID_CUSTOMER_KEY);
        }

        return savedCustomerKey;
    }

    @Transactional
    public void ensureSingleActiveBillingMethodIsDefault(Long memberId) {
        List<Billing> activeBillingMethods =
                billingRepository.findByMemberIdAndBillingStatus(memberId, BillingStatus.ACTIVE);

        if (activeBillingMethods.size() == 1) {
            activeBillingMethods.get(0).markDefault();
        }
    }

    private boolean shouldRegisterAsDefault(Long memberId) {
        long activeBillingMethodCount =
                billingRepository.countByMemberIdAndBillingStatus(memberId, BillingStatus.ACTIVE);

        if (activeBillingMethodCount == 0) {
            return true;
        }

        return !billingRepository.existsByMemberIdAndBillingStatusAndIsDefaultTrue(
                memberId,
                BillingStatus.ACTIVE
        );
    }

    private Billing saveBilling(
            Long memberId,
            TossBillingKeyIssueResponse tossResponse,
            boolean isDefault
    ) {
        Billing billing = Billing.builder()
                .memberId(memberId)
                .billingKey(tossResponse.billingKey())
                .issuerCode(CardCompany.fromCode(tossResponse.card().issuerCode()))
                .cardNumber(tossResponse.card().number())
                .cardType(CardType.from(tossResponse.card().cardType()))
                .ownerType(OwnerType.from(tossResponse.card().ownerType()))
                .isDefault(isDefault)
                .billingStatus(BillingStatus.ACTIVE)
                .build();

        return billingRepository.save(billing);
    }
}
