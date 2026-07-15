package com.haagendazs.payment.order.repository;

import com.haagendazs.payment.order.entity.Orders;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Orders, Long> {
    Optional<Orders> findByOrderNo(String orderNo);

    List<Orders> findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(Long memberId, Long workspaceId);

    Optional<Orders> findByOrderNoAndMemberIdAndWorkspaceId(String orderNo, Long memberId, Long workspaceId);

    Optional<Orders> findByMemberIdAndWorkspaceIdAndIdempotencyKey(
            Long memberId,
            Long workspaceId,
            String idempotencyKey
    );
}
