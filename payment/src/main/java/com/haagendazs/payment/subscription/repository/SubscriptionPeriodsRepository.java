package com.haagendazs.payment.subscription.repository;

import com.haagendazs.payment.subscription.entity.SubscriptionPeriods;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPeriodsRepository extends JpaRepository<SubscriptionPeriods, Long> {

    List<SubscriptionPeriods> findByWorkspaceIdOrderByPeriodStartDesc(Long workspaceId);
}
