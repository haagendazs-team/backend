package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Payments;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payments, Long> {

    Optional<Payments> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
