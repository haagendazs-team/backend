package com.haagendazs.payment.subscription.repository;

import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionScheduledChangesRepository
        extends JpaRepository<SubscriptionScheduledChanges, Long> {

    List<SubscriptionScheduledChanges> findByChangeTypeAndChangeStatusAndScheduledAtLessThanEqual(
            SubscriptionChangeType changeType,
            SubscriptionChangeStatus changeStatus,
            LocalDateTime scheduledAt
    );
}
