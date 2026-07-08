package com.haagendazs.payment.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.payment.entity.PaymentCustomerKey;
import com.haagendazs.payment.payment.repository.PaymentCustomerKeyRepository;
import com.haagendazs.payment.payment.service.tools.CustomerKeyEncryptor;
import com.haagendazs.payment.payment.service.tools.CustomerKeyHashEncoder;
import com.haagendazs.payment.payment.service.tools.TossCustomerKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCustomerKeyService {

    private final PaymentCustomerKeyRepository paymentCustomerKeyRepository;
    private final TossCustomerKeyGenerator tossCustomerKeyGenerator;
    private final CustomerKeyEncryptor customerKeyEncryptor;
    private final CustomerKeyHashEncoder customerKeyHashEncoder;

    @Transactional
    public String getOrCreateCustomerKey(Long memberId) {
        return paymentCustomerKeyRepository.findByMemberId(memberId)
                .map(this::decryptCustomerKey)
                .orElseGet(() -> createCustomerKey(memberId));
    }

    @Transactional(readOnly = true)
    public String getCustomerKey(Long memberId) {
        PaymentCustomerKey customerKey = paymentCustomerKeyRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.CUSTOMER_KEY_NOT_FOUND));

        return decryptCustomerKey(customerKey);
    }

    @Transactional
    public void deleteCustomerKey(Long memberId) {
        paymentCustomerKeyRepository.deleteByMemberId(memberId);
    }

    private String createCustomerKey(Long memberId) {
        String rawCustomerKey = generateUniqueCustomerKey();

        String encrypted = customerKeyEncryptor.encrypt(rawCustomerKey);
        String hash = customerKeyHashEncoder.encode(rawCustomerKey);

        PaymentCustomerKey entity = PaymentCustomerKey.create(
                memberId,
                encrypted,
                hash
        );

        paymentCustomerKeyRepository.save(entity);

        return rawCustomerKey;
    }

    private String generateUniqueCustomerKey() {
        for (int i = 0; i < 5; i++) {
            String customerKey = tossCustomerKeyGenerator.generate();
            String hash = customerKeyHashEncoder.encode(customerKey);

            if (!paymentCustomerKeyRepository.existsByCustomerKeyHash(hash)) {
                return customerKey;
            }
        }

        throw new BusinessException(PaymentErrorCode.CUSTOMER_KEY_GENERATION_FAILED);
    }

    private String decryptCustomerKey(PaymentCustomerKey customerKey) {
        return customerKeyEncryptor.decrypt(customerKey.getCustomerKeyEncrypted());
    }
}
