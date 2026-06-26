package com.haagendazs.application.service;

import com.haagendazs.application.dto.NotificationResult;
import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.application.sender.NotificationSender;
import com.haagendazs.domain.model.*;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.HistoryRepository;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.presentation.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class Dispatcher {

    private final NotificationRepository notificationRepository;
    private final ChannelRepository channelRepository;
    private final HistoryRepository historyRepository;
    private final SettingEntryRepository settingEntryRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Map<ChannelType, NotificationSender> senderMap;

    public Dispatcher(
            NotificationRepository notificationRepository,
            ChannelRepository channelRepository,
            HistoryRepository historyRepository,
            SettingEntryRepository settingEntryRepository,
            SseNotificationPort sseNotificationPort,
            List<NotificationSender> senders
    ) {
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.historyRepository = historyRepository;
        this.settingEntryRepository = settingEntryRepository;
        this.sseNotificationPort = sseNotificationPort;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channelType, Function.identity()));
    }

    public Mono<Boolean> toMember(Event event, Long memberId, String payload) {
        return notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.just(true).filter(v -> false))
                .flatMap(ignored -> settingEntryRepository
                        .findByMemberIdAndEventTypeCode(memberId, event.getEventTypeCode())
                        .map(SettingEntry::isEnabled)
                        .defaultIfEmpty(true))
                .filter(enabled -> enabled)
                .flatMap(ignored -> notificationRepository.save(Notification.create(memberId, event.getId())))
                .flatMap(notification -> {
                    NotificationResponse ssePayload = NotificationResponse.from(
                            NotificationResult.of(notification, event));
                    return channelRepository.findByMemberIdAndEnabledTrue(memberId)
                            .collectList()
                            .flatMap(channels -> {
                                List<Channel> emailChannels = channels.stream()
                                        .filter(c -> c.getChannelType() == ChannelType.EMAIL)
                                        .toList();
                                List<Channel> nonEmailChannels = channels.stream()
                                        .filter(c -> c.getChannelType() != ChannelType.EMAIL)
                                        .toList();

                                return sendToChannels(notification.getId(), nonEmailChannels, event.getTypeName(), payload)
                                        .collectList()
                                        .flatMap(results -> {
                                            boolean anyFailed = results.stream().anyMatch(f -> !f);
                                            sendSseAndEmailAsync(memberId, ssePayload, notification.getId(),
                                                    emailChannels, event.getTypeName(), payload);
                                            return Mono.just(anyFailed);
                                        });
                            });
                })
                .defaultIfEmpty(false);
    }

    public Mono<History> sendToChannelAndBuildHistory(Long notificationId, Channel channel,
                                                       String subject, String body) {
        NotificationSender sender = senderMap.get(channel.getChannelType());
        if (sender == null) {
            log.warn("지원하지 않는 채널 타입 channelType={} notificationId={}", channel.getChannelType(), notificationId);
            return Mono.just(History.failed(notificationId, channel.getChannelType(), "지원하지 않는 채널 타입"));
        }
        return Mono.fromCallable(() -> {
                    sender.send(channel.getChannelTarget(), subject, body);
                    return History.sent(notificationId, channel.getChannelType());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(
                        History.failed(notificationId, channel.getChannelType(), e.getMessage())));
    }

    private Flux<Boolean> sendToChannels(Long notificationId, List<Channel> channels,
                                          String subject, String body) {
        return Flux.fromIterable(channels)
                .flatMap(channel -> sendToChannel(notificationId, channel, subject, body));
    }

    private Mono<Boolean> sendToChannel(Long notificationId, Channel channel,
                                         String subject, String body) {
        NotificationSender sender = senderMap.get(channel.getChannelType());
        if (sender == null) {
            log.warn("지원하지 않는 채널 타입 channelType={} notificationId={}", channel.getChannelType(), notificationId);
            return historyRepository.save(
                    History.failed(notificationId, channel.getChannelType(), "지원하지 않는 채널 타입"))
                    .thenReturn(false);
        }
        return Mono.fromCallable(() -> {
                    sender.send(channel.getChannelTarget(), subject, body);
                    return History.sent(notificationId, channel.getChannelType());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(
                        History.failed(notificationId, channel.getChannelType(), e.getMessage())))
                .flatMap(history -> historyRepository.save(history).thenReturn(!history.isFailed()));
    }

    private void sendSseAndEmailAsync(Long memberId, NotificationResponse ssePayload, Long notificationId,
                                       List<Channel> emailChannels, String subject, String body) {
        sseNotificationPort.send(memberId, ssePayload);
        if (!emailChannels.isEmpty()) {
            Flux.fromIterable(emailChannels)
                    .flatMap(channel -> sendToChannel(notificationId, channel, subject, body))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }
    }
}
