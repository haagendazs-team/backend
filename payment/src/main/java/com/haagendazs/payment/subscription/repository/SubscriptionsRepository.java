package com.haagendazs.payment.subscription.repository;

import com.haagendazs.payment.subscription.entity.SubscriptionPeriods;
import com.haagendazs.payment.subscription.entity.Subscriptions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {

    boolean existsByWorkspaceId(Long workspaceId);
    Optional<Subscriptions> findByWorkspaceId(Long workspaceId);

    @Query("""
        select s
        from Subscriptions s
        where s.status = 'ACTIVE'
          and s.currentPeriodEnd <= :now
          and s.subscriptionPlanId <> :normalPlanId
    """)
    List<Subscriptions> findExpiredPaidSubscriptions(
            @Param("now") LocalDateTime now,
            @Param("normalPlanId") Long normalPlanId
    );
}
