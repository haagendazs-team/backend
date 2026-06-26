package com.haagendazs.infrastructure.consumer;

import com.haagendazs.application.listener.NotificationPermanentlyFailedEvent;
import com.haagendazs.application.service.EventStatusService;
import com.haagendazs.application.service.FanoutService;
import com.haagendazs.application.service.PayloadParser;
import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.model.EventStatus;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.model.Notification;
import com.haagendazs.domain.repository.EventRepository;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class PelMessageProcessor {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final FanoutService fanoutService;
    private final EventStatusService statusService;
    private final EventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final PayloadParser payloadParser;
    private final NotificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final List<Integer> backoffMinutes;

    public PelMessageProcessor(ReactiveStringRedisTemplate redisTemplate,
                                 FanoutService fanoutService,
                                 EventStatusService statusService,
                                 EventRepository eventRepository,
                                 NotificationRepository notificationRepository,
                                 PayloadParser payloadParser,
                                 NotificationProperties properties,
                                 ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.fanoutService = fanoutService;
        this.statusService = statusService;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.payloadParser = payloadParser;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.backoffMinutes = properties.pel().backoffMinutes();
    }

    public Mono<Void> process(String streamKey, EventTypeDefinition definition,
                               MapRecord<String, Object, Object> message) {
        String payload = String.valueOf(message.getValue().get(ReactiveRedisStreamEventPublisher.PAYLOAD_KEY));
        String streamMessageId = message.getId().getValue();

        return statusService.saveEventWithStreamMessageId(definition, payload, streamMessageId)
                .flatMap(event -> {
                    if (isAlreadyResolved(event)) {
                        return acknowledge(streamKey, message)
                                .doOnSuccess(v -> log.info("PEL 이벤트 ACK (이미 처리됨) eventId={} status={}", event.getId(), event.getStatus()));
                    }
                    if (isScheduledPending(event)) {
                        return acknowledge(streamKey, message)
                                .doOnSuccess(v -> log.info("PEL 이벤트 ACK (예약 미도래) streamKey={} id={}", streamKey, message.getId()));
                    }
                    return fanoutService.fanout(event, definition, payload)
                            .flatMap(failed -> {
                                if (!failed) {
                                    return statusService.markEventStatus(event.getId(), false)
                                            .then(acknowledge(streamKey, message))
                                            .doOnSuccess(v -> log.info("PEL 재처리 완료 streamKey={} id={}", streamKey, message.getId()));
                                }
                                return handleExhausted(event, definition, streamKey, message);
                            });
                })
                .onErrorResume(e -> {
                    log.error("PEL 재처리 실패 streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    public Duration resolveBackoff(int retryCount) {
        int index = Math.min(retryCount, backoffMinutes.size() - 1);
        return Duration.ofMinutes(backoffMinutes.get(index));
    }

    private Mono<Void> handleExhausted(Event event, EventTypeDefinition definition,
                                        String streamKey, MapRecord<String, Object, Object> message) {
        if (!event.incrementRetryAndCheckExhausted(backoffMinutes.size())) {
            return eventRepository.save(event)
                    .then()
                    .doOnSuccess(v -> log.warn("PEL 재처리 발송 실패 streamKey={} id={} retryCount={}", streamKey, message.getId(), event.getRetryCount()));
        }
        event.markPermanentlyFailed();
        return eventRepository.save(event)
                .flatMap(saved -> saveNotificationForPermanentlyFailed(saved, definition))
                .then(acknowledge(streamKey, message))
                .doOnSuccess(v -> {
                    log.error("PEL 재처리 모두 소진, 영구 실패 처리 eventId={} retryCount={}", event.getId(), event.getRetryCount());
                    eventPublisher.publishEvent(NotificationPermanentlyFailedEvent.of(
                            event.getId(), event.getEventTypeCode(), event.getRetryCount(), "PEL", event.getPayload()));
                });
    }

    private Mono<Void> saveNotificationForPermanentlyFailed(Event event, EventTypeDefinition definition) {
        if (!definition.isSingleTarget()) {
            return Mono.empty();
        }
        try {
            Long memberId = payloadParser.extractMemberId(event.getPayload(), definition);
            return notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)
                    .filter(exists -> !exists)
                    .flatMap(ignored -> notificationRepository.save(Notification.create(memberId, event.getId())))
                    .then();
        } catch (Exception e) {
            log.error("영구 실패 알림 기록 저장 실패 eventId={}", event.getId(), e);
            return Mono.empty();
        }
    }

    private boolean isAlreadyResolved(Event event) {
        EventStatus status = event.getStatus();
        return status == EventStatus.PUBLISHED || status == EventStatus.PERMANENTLY_FAILED;
    }

    private boolean isScheduledPending(Event event) {
        return event.isScheduled() && event.getStatus() == EventStatus.PENDING;
    }

    private Mono<Void> acknowledge(String streamKey, MapRecord<String, Object, Object> message) {
        return redisTemplate.opsForStream()
                .acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId())
                .then();
    }
}
