package com.haagendazs.payment.Subscription.repository;

import com.haagendazs.payment.Subscription.entity.SubscriptionPeriods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionScheduledChangesRepository extends JpaRepository<SubscriptionPeriods, Long> {
}
