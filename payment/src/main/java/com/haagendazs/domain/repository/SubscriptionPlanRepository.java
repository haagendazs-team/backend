package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.SubscriptionPlan;
import com.haagendazs.domain.model.PlanType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByType(PlanType planType);
}
