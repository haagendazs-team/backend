package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

public interface NotificationRepository {
    Mono<Notification> save(Notification notification);
    Flux<Notification> saveAll(List<Notification> notifications);
    Mono<Set<Long>> findExistingMemberIdsByEventId(Long eventId, List<Long> memberIds);
    Mono<Notification> findByIdAndMemberId(Long id, Long memberId);
    Flux<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, long offset, int limit);
    Mono<Long> countByMemberId(Long memberId);
    Mono<Boolean> existsByEventIdAndMemberId(Long eventId, Long memberId);
    Mono<Void> markAllReadByMemberId(Long memberId);
    Flux<Notification> findByMemberIdAndIdGreaterThanOrderByIdAsc(Long memberId, Long lastId, int limit);
}
