package com.haagendazs.application.service;

import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.*;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.HistoryRepository;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkPersistService {

    private final NotificationRepository notificationRepository;
    private final HistoryRepository historyRepository;
    private final ChannelRepository channelRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Dispatcher dispatcher;

    public Mono<Boolean> persist(Event event, List<Notification> notifications, String payload) {
        return notificationRepository.saveAll(notifications)
                .collectList()
                .flatMap(saved -> loadChannelsByMember(saved)
                        .flatMap(channelsByMember -> Flux.fromIterable(saved)
                                .flatMap(notification -> sendAndRecord(
                                        notification.getId(),
                                        channelsOf(channelsByMember, notification.getMemberId()),
                                        event.getTypeName(), payload)
                                        .any(History::isFailed))
                                .any(failed -> failed)));
    }

    public Mono<Void> persistBuffered(List<BufferItem> items) {
        Map<Long, BufferItem> itemByMemberId = indexByMemberId(items);
        List<Notification> notifications = items.stream().map(BufferItem::notification).toList();
        return notificationRepository.saveAll(notifications)
                .collectList()
                .flatMap(saved -> loadChannelsByMember(saved)
                        .flatMap(channelsByMember -> Flux.fromIterable(saved)
                                .flatMap(notification -> {
                                    BufferItem item = itemByMemberId.get(notification.getMemberId());
                                    return sendAndRecord(
                                            notification.getId(),
                                            channelsOf(channelsByMember, notification.getMemberId()),
                                            item.subject(), item.payload())
                                            .then(Mono.fromRunnable(() -> sendSseAfterPersist(notification, item)));
                                })
                                .then()));
    }

    private Mono<Map<Long, Collection<Channel>>> loadChannelsByMember(List<Notification> saved) {
        List<Long> memberIds = saved.stream().map(Notification::getMemberId).toList();
        return channelRepository.findByMemberIdInAndEnabledTrue(memberIds)
                .collectMultimap(Channel::getMemberId);
    }

    private Flux<History> sendAndRecord(Long notificationId, Collection<Channel> channels,
                                        String subject, String payload) {
        return Flux.fromIterable(channels)
                .flatMap(c -> dispatcher.sendToChannelAndBuildHistory(notificationId, c, subject, payload))
                .flatMap(historyRepository::save);
    }

    private Collection<Channel> channelsOf(Map<Long, Collection<Channel>> channelsByMember, Long memberId) {
        return channelsByMember.getOrDefault(memberId, List.of());
    }

    private Map<Long, BufferItem> indexByMemberId(List<BufferItem> items) {
        return items.stream()
                .collect(Collectors.toMap(i -> i.notification().getMemberId(), i -> i, (a, b) -> a));
    }

    private void sendSseAfterPersist(Notification notification, BufferItem item) {
        try {
            NotificationResponse ssePayload = new NotificationResponse(
                    notification.getId(),
                    item.subject(),
                    item.payload(),
                    notification.isAlreadyRead(),
                    notification.getCreatedAt() != null
                            ? notification.getCreatedAt().toInstant(ZoneOffset.UTC)
                            : Instant.now()
            );
            sseNotificationPort.send(notification.getMemberId(), ssePayload);
        } catch (Exception e) {
            log.warn("SSE 발송 실패 memberId={} eventType={}", notification.getMemberId(), item.subject());
        }
    }
}
