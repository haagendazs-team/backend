package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.SubscriptionPeriods;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPeriodsRepository extends JpaRepository<SubscriptionPeriods, Long> {

    List<SubscriptionPeriods> findByWorkspaceIdOrderByPeriodStartDesc(Long workspaceId);
}
