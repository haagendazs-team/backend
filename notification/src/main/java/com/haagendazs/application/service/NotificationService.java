package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.NotificationErrorCode;
import com.haagendazs.application.dto.NotificationResult;
import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.EventRepository;
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
    private final ChannelRepository channelRepository;
    private final SseNotificationPort sseNotificationPort;

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
        Flux<ServerSentEvent<Object>> replay = buildReplay(memberId, lastEventId);
        // query 이 부분을 200 메시징 or 5초후 조회
        // sse 와 채널은 분리해도 될득해 이메일은 빼도 될듯한데
        // 왜냐하면 즉시 발송이 로직에서 알림함 이동-> 이메일 타입은
        // 이메일 채널은 사실상 몇 없어 ( 결제 완료 도메인만 포함된거니)
        // 1분마다 결제 완료된 걸 조회해서 이메일을 보내는 로직을 분리하는게 좋은것 같아
        // k6에서 결제 알림을 비활성화 해서 이메일을 못보내게 해서 딜레이를 줄여야해
        // 왜냐하면 이메일 실패시 1명당 3초 걸리거든
        // 즉시 발동으로 하고
        return channelRepository.findByMemberIdAndEnabledTrue(memberId)
                .collectList()
                .flatMapMany(channels -> sseNotificationPort.subscribe(memberId, channels, replay));
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
