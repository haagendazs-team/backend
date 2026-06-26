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
import reactor.core.scheduler.Schedulers;

import java.time.ZoneOffset;
import java.util.List;

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
                .flatMap(saved -> {
                    List<Long> memberIds = saved.stream().map(Notification::getMemberId).toList();
                    return channelRepository.findByMemberIdInAndEnabledTrue(memberIds)
                            .collectMultimap(Channel::getMemberId)
                            .flatMap(channelsByMember -> Flux.fromIterable(saved)
                                    .flatMap(notification -> {
                                        List<Channel> channels = (List<Channel>)
                                                channelsByMember.getOrDefault(notification.getMemberId(), List.of());
                                        List<Channel> emailChannels = channels.stream()
                                                .filter(c -> c.getChannelType() == ChannelType.EMAIL).toList();
                                        List<Channel> nonEmailChannels = channels.stream()
                                                .filter(c -> c.getChannelType() != ChannelType.EMAIL).toList();

                                        return Flux.fromIterable(nonEmailChannels)
                                                .flatMap(c -> dispatcher.sendToChannelAndBuildHistory(
                                                        notification.getId(), c, event.getTypeName(), payload))
                                                .flatMap(historyRepository::save)
                                                .any(History::isFailed)
                                                .doOnSuccess(ignored -> sendEmailAndSseAsync(
                                                        notification, emailChannels, event, payload));
                                    })
                                    .any(failed -> failed));
                });
    }

    public Mono<Void> persistBuffered(List<BufferItem> items) {
        List<Notification> notifications = items.stream().map(BufferItem::notification).toList();
        return notificationRepository.saveAll(notifications)
                .collectList()
                .flatMap(saved -> {
                    List<Long> memberIds = saved.stream().map(Notification::getMemberId).toList();
                    return channelRepository.findByMemberIdInAndEnabledTrue(memberIds)
                            .collectMultimap(Channel::getMemberId)
                            .flatMap(channelsByMember -> {
                                java.util.Map<Long, BufferItem> itemByMemberId = items.stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                i -> i.notification().getMemberId(),
                                                i -> i,
                                                (a, b) -> a));

                                return Flux.fromIterable(saved)
                                        .flatMap(notification -> {
                                            BufferItem item = itemByMemberId.get(notification.getMemberId());
                                            List<Channel> channels = (List<Channel>)
                                                    channelsByMember.getOrDefault(notification.getMemberId(), List.of());

                                            return Flux.fromIterable(channels)
                                                    .filter(c -> c.getChannelType() != ChannelType.EMAIL)
                                                    .flatMap(c -> dispatcher.sendToChannelAndBuildHistory(
                                                            notification.getId(), c, item.subject(), item.payload()))
                                                    .flatMap(historyRepository::save)
                                                    .then(Mono.fromRunnable(() -> sendSseAfterPersist(notification, item)));
                                        })
                                        .then();
                            });
                });
    }

    private void sendEmailAndSseAsync(Notification notification, List<Channel> emailChannels,
                                       Event event, String payload) {
        Flux.fromIterable(emailChannels)
                .flatMap(c -> dispatcher.sendToChannelAndBuildHistory(notification.getId(), c, event.getTypeName(), payload))
                .flatMap(historyRepository::save)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private void sendSseAfterPersist(Notification notification, BufferItem item) {
        try {
            NotificationResponse ssePayload = new NotificationResponse(
                    notification.getId(),
                    item.subject(),
                    item.payload(),
                    notification.isAlreadyRead(),
                    notification.getCreatedAt().toInstant(ZoneOffset.UTC)
            );
            sseNotificationPort.send(notification.getMemberId(), ssePayload);
        } catch (Exception e) {
            log.warn("SSE 발송 실패 memberId={} eventType={}", notification.getMemberId(), item.subject());
        }
    }
}
