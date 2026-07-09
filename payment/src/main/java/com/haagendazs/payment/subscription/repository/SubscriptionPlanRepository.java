package com.haagendazs.payment.subscription.repository;

import com.haagendazs.payment.subscription.entity.SubscriptionPlan;
import com.haagendazs.payment.subscription.enums.PlanType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByType(PlanType planType);
}
