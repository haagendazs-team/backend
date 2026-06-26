package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.application.dto.NotificationResult;
import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.Setting;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.EventRepository;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.domain.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EventRepository eventRepository;
    private final SettingRepository settingRepository;
    private final ChannelRepository channelRepository;
    private final SseNotificationPort sseNotificationPort;

    @Transactional(readOnly = true)
    public Flux<NotificationResult> getNotifications(Long memberId, long offset, int limit) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, offset, limit)
                .flatMap(notification -> eventRepository.findById(notification.getEventId())
                        .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)))
                        .map(event -> NotificationResult.of(notification, event)));
    }

    public Mono<Long> countNotifications(Long memberId) {
        return notificationRepository.countByMemberId(memberId);
    }

    @Transactional
    public Mono<Void> markRead(Long memberId, Long notificationId) {
        return notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)))
                .flatMap(notification -> {
                    if (notification.isAlreadyRead()) {
                        return Mono.error(new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ));
                    }
                    notification.markRead();
                    return notificationRepository.save(notification).then();
                });
    }

    @Transactional
    public Mono<Void> markAllRead(Long memberId) {
        return notificationRepository.markAllReadByMemberId(memberId);
    }

    public Flux<ServerSentEvent<Object>> subscribe(Long memberId) {
        return settingRepository.findByMemberId(memberId)
                .defaultIfEmpty(Setting.createDefault(memberId))
                .flatMapMany(setting -> channelRepository.findByMemberIdAndEnabledTrue(memberId)
                        .collectList()
                        .flatMapMany(channels -> sseNotificationPort.subscribe(memberId, setting, channels)));
    }
}
