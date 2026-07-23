package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.PaymentRetryJob;
import com.haagendazs.domain.model.PaymentRetryJobStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRetryJobRepository extends JpaRepository<PaymentRetryJob, Long> {

    List<PaymentRetryJob> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            PaymentRetryJobStatus status,
            LocalDateTime nextRetryAt,
            Pageable pageable
    );
}
