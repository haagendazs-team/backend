package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.PaymentCustomerKey;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;


public interface PaymentCustomerKeyRepository
        extends JpaRepository<PaymentCustomerKey, Long> {

    Optional<PaymentCustomerKey> findByMemberId(Long memberId);

    boolean existsByCustomerKeyHash(String customerKeyHash);

    void deleteByMemberId(Long memberId);
}
