package com.haagendazs.payment.subscription.repository;

import com.haagendazs.payment.subscription.entity.SubscriptionScheduledChanges;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeStatus;
import com.haagendazs.payment.subscription.enums.SubscriptionChangeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    List<SubscriptionScheduledChanges> findBySubscriptionIdAndMemberIdAndChangeTypeAndChangeStatus(
            Long subscriptionId,
            Long memberId,
            SubscriptionChangeType changeType,
            SubscriptionChangeStatus changeStatus
    );

    List<SubscriptionScheduledChanges> findBySubscriptionIdAndChangeTypeAndChangeStatus(
            Long subscriptionId,
            SubscriptionChangeType changeType,
            SubscriptionChangeStatus changeStatus
    );

    Optional<SubscriptionScheduledChanges> findFirstBySubscriptionIdAndChangeTypeAndChangeStatusOrderByScheduledAtDesc(
            Long subscriptionId,
            SubscriptionChangeType changeType,
            SubscriptionChangeStatus changeStatus
    );
}
