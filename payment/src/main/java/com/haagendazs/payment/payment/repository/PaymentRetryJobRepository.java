package com.haagendazs.payment.payment.repository;

import com.haagendazs.payment.payment.entity.PaymentRetryJob;
import com.haagendazs.payment.payment.enums.PaymentRetryJobStatus;
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
