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

    // 프론트에서 requestBillingAuth를 호출할 때 사용할 customerKey를 준비합니다.
    @Transactional
    public BillingMethodPrepareResponse prepareRegistration(Long memberId) {
        String customerKey = paymentCustomerKeyService.getOrCreateCustomerKey(memberId);

        return new BillingMethodPrepareResponse(customerKey);
    }

    // authKey로 Toss billingKey를 발급하고 결제수단을 저장합니다. customerKey는 클라이언트 값 대신 DB에서 조회합니다.
    @Transactional
    public Billing issueBillingMethod(Long memberId, BillingMethodIssueRequest request) {
        validateBillingMethodLimit(memberId);
        String customerKey = paymentCustomerKeyService.getCustomerKey(memberId);

        TossBillingKeyIssueResponse tossResponse =
                tossBillingClient.issueBillingKey(
                        request.authKey(),
                        customerKey
                );

        return saveBilling(memberId, tossResponse, shouldRegisterAsDefault(memberId));
    }

    // Toss billingKey를 삭제한 뒤 로컬 결제수단을 비활성화합니다.
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

    // 활성 결제수단이 하나만 남으면 기본 결제수단으로 보정합니다.
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
