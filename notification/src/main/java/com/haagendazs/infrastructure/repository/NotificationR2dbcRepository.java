package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Notification;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NotificationR2dbcRepository extends ReactiveCrudRepository<Notification, Long> {

    Mono<Notification> findByIdAndMemberId(Long id, Long memberId);

    @Query("SELECT * FROM notification.notifications WHERE member_id = :memberId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, long offset, int limit);

    @Query("SELECT COUNT(*) FROM notification.notifications WHERE member_id = :memberId")
    Mono<Long> countByMemberId(Long memberId);

    @Query("SELECT EXISTS(SELECT 1 FROM notification.notifications WHERE event_id = :eventId AND member_id = :memberId)")
    Mono<Boolean> existsByEventIdAndMemberId(Long eventId, Long memberId);

    @Query("SELECT member_id FROM notification.notifications WHERE event_id = :eventId AND member_id = ANY(:memberIds)")
    Flux<Long> findMemberIdsByEventIdAndMemberIdIn(Long eventId, Long[] memberIds);

    @Query("SELECT * FROM notification.notifications WHERE member_id = :memberId AND id > :lastId ORDER BY id ASC LIMIT :limit")
    Flux<Notification> findByMemberIdAndIdGreaterThanOrderByIdAsc(Long memberId, Long lastId, int limit);

    @Modifying
    @Query("UPDATE notification.notifications SET is_read = true WHERE member_id = :memberId AND is_read = false")
    Mono<Void> markAllReadByMemberId(Long memberId);
}
