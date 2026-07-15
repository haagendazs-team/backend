package com.haagendazs.payment.payment.repository;

import com.haagendazs.payment.payment.entity.Payments;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payments, Long> {

    Optional<Payments> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
