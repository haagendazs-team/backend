package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.NotificationErrorCode;
import com.haagendazs.application.dto.NotificationResult;
import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.application.port.SettingCachePort;
import com.haagendazs.domain.repository.EventRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import java.util.List;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int REPLAY_LIMIT = 200;

    private final NotificationRepository notificationRepository;
    private final EventRepository eventRepository;
    private final SseNotificationPort sseNotificationPort;
    private final SettingEntryRepository settingEntryRepository;
    private final SettingCachePort settingCachePort;

    @Transactional(readOnly = true)
    public Flux<NotificationResult> getNotifications(Long memberId, long offset, int limit) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, offset, limit)
                .flatMap(notification -> eventRepository.findById(notification.getEventId())
                        .switchIfEmpty(Mono.error(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)))
                        .map(event -> NotificationResult.of(notification, event)));
    }

    public Mono<Long> countNotifications(Long memberId) {
        return notificationRepository.countByMemberId(memberId);
    }

    @Transactional
    public Mono<Void> markRead(Long memberId, Long notificationId) {
        return notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .switchIfEmpty(Mono.error(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)))
                .flatMap(notification -> {
                    if (notification.isAlreadyRead()) {
                        return Mono.error(new BusinessException(NotificationErrorCode.NOTIFICATION_ALREADY_READ));
                    }
                    notification.markRead();
                    return notificationRepository.save(notification).then();
                });
    }

    @Transactional
    public Mono<Void> markAllRead(Long memberId) {
        return notificationRepository.markAllReadByMemberId(memberId);
    }

    public Flux<ServerSentEvent<Object>> subscribe(Long memberId, Long lastEventId) {
        Mono<Void> cacheWarmup = settingCachePort.isCached(memberId)
                .filter(cached -> !cached)
                .flatMap(ignored -> settingEntryRepository.findAllByMemberId(memberId)
                        .collectList()
                        .flatMap(entries -> settingCachePort.putAll(memberId, entries)));

        return cacheWarmup
                .thenMany(sseNotificationPort.subscribe(memberId, List.of(), buildReplay(memberId, lastEventId)));
    }

    private Flux<ServerSentEvent<Object>> buildReplay(Long memberId, Long lastEventId) {
        if (lastEventId == null) {
            return Flux.empty();
        }

        return notificationRepository.findByMemberIdAndIdGreaterThanOrderByIdAsc(memberId, lastEventId, REPLAY_LIMIT)
                .flatMap(notification -> eventRepository.findById(notification.getEventId())
                        .map(event -> NotificationResult.of(notification, event)))
                .map(result -> ServerSentEvent.builder()
                        .id(String.valueOf(result.id()))
                        .event("notification")
                        .data(NotificationResponse.from(result))
                        .build());
    }
}
