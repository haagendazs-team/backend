package com.haagendazs.payment.payment.repository;

import com.haagendazs.payment.payment.entity.Billing;
import com.haagendazs.payment.payment.enums.BillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {

    Optional<Billing> findFirstByMemberIdAndBillingStatusAndIsDefaultTrueOrderByIdDesc(
            Long memberId,
            BillingStatus billingStatus
    );

    Optional<Billing> findByIdAndMemberIdAndBillingStatus(
            Long id,
            Long memberId,
            BillingStatus billingStatus
    );

    Optional<Billing> findByIdAndBillingStatus(
            Long id,
            BillingStatus billingStatus
    );

    long countByMemberIdAndBillingStatus(Long memberId, BillingStatus billingStatus);

    boolean existsByMemberIdAndBillingStatusAndIsDefaultTrue(
            Long memberId,
            BillingStatus billingStatus
    );

    List<Billing> findByMemberIdAndBillingStatus(Long memberId, BillingStatus billingStatus);
}
