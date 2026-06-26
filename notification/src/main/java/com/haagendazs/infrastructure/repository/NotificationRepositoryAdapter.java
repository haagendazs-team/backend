package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Notification;
import com.haagendazs.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Notification> save(Notification notification) {
        return r2dbcRepository.save(notification);
    }

    @Override
    public Flux<Notification> saveAll(List<Notification> notifications) {
        return r2dbcRepository.saveAll(notifications);
    }

    @Override
    public Mono<Set<Long>> findExistingMemberIdsByEventId(Long eventId, List<Long> memberIds) {
        return r2dbcRepository.findMemberIdsByEventIdAndMemberIdIn(eventId, memberIds.toArray(Long[]::new))
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Mono<Notification> findByIdAndMemberId(Long id, Long memberId) {
        return r2dbcRepository.findByIdAndMemberId(id, memberId);
    }

    @Override
    public Flux<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, long offset, int limit) {
        return r2dbcRepository.findByMemberIdOrderByCreatedAtDesc(memberId, offset, limit);
    }

    @Override
    public Mono<Long> countByMemberId(Long memberId) {
        return r2dbcRepository.countByMemberId(memberId);
    }

    @Override
    public Mono<Boolean> existsByEventIdAndMemberId(Long eventId, Long memberId) {
        return r2dbcRepository.existsByEventIdAndMemberId(eventId, memberId);
    }

    @Override
    public Mono<Void> markAllReadByMemberId(Long memberId) {
        return r2dbcRepository.markAllReadByMemberId(memberId);
    }
}
