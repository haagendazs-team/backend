package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Billing;
import com.haagendazs.domain.model.BillingStatus;
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
