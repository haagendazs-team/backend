package com.haagendazs.infrastructure.consumer;

import com.haagendazs.application.service.EventStatusService;
import com.haagendazs.application.service.FanoutService;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import com.haagendazs.presentation.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamSubscriptionManager {

    private final StreamReceiver<String, ObjectRecord<String, String>> receiver;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final EventStatusService statusService;
    private final FanoutService fanoutService;
    private final EventTypeRegistry registry;

    @Value("${spring.application.name:app}-consumer-${HOSTNAME:local}")
    private String consumerName;

    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    public void startSubscription(EventTypeDefinition definition) {
        String streamKey = definition.getStreamKey();
        if (subscriptions.containsKey(streamKey)) {
            log.debug("이미 구독 중인 stream 스킵 streamKey={}", streamKey);
            return;
        }
        createGroupIfAbsent(streamKey);
        Disposable disposable = receiver.receive(
                        Consumer.from(RedisStreamsConfig.NOTIFICATION_GROUP, consumerName),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
                .flatMap(message -> handleMessage(definition, message))
                .subscribe(
                        v -> {},
                        e -> log.error("Stream 구독 오류 streamKey={}", streamKey, e)
                );
        if (subscriptions.putIfAbsent(streamKey, disposable) != null) {
            disposable.dispose();
            log.debug("중복 구독 방지 처리 streamKey={}", streamKey);
        }
    }

    public boolean isSubscribed(String streamKey) {
        return subscriptions.containsKey(streamKey);
    }

    private void createGroupIfAbsent(String streamKey) {
        try {
            redisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), RedisStreamsConfig.NOTIFICATION_GROUP)
                    .block();
        } catch (Exception e) {
            if (!isBusyGroup(e)) {
                log.error("Consumer group 생성 실패 stream={}", streamKey, e);
            }
        }
    }

    // TODO(Task 6): EventStatusService/FanoutService 시그니처가 EventTypeDefinition으로 변경되면
    // EventType.fromStreamKey 브릿지 코드를 제거하고 definition을 직접 전달한다.
    private Flux<Void> handleMessage(EventTypeDefinition definition,
                                      ObjectRecord<String, String> message) {
        String streamKey = definition.getStreamKey();
        EventType eventType = EventType.fromStreamKey(streamKey).orElse(null);
        if (eventType == null) {
            log.warn("미등록 streamKey 수신 — 처리 건너뜀 streamKey={}", streamKey);
            return Flux.empty();
        }
        return statusService.saveEventWithStreamMessageId(eventType, message.getValue(), message.getId().getValue())
                .flatMapMany(event -> {
                    if (event.isScheduled()) {
                        return acknowledge(streamKey, message)
                                .doOnSuccess(v -> log.info("예약 알림 저장 완료 ACK streamKey={} id={}", streamKey, message.getId()))
                                .flux();
                    }
                    return fanoutService.fanoutBuffered(event, eventType, message.getValue(), streamKey, message.getId())
                            .then(statusService.markEventStatus(event.getId(), false))
                            .doOnSuccess(v -> log.info("버퍼 enqueue 완료 streamKey={} id={}", streamKey, message.getId()))
                            .flux();
                })
                .onErrorResume(e -> {
                    log.error("Stream 처리 실패 streamKey={} id={}", streamKey, message.getId(), e);
                    return Flux.empty();
                });
    }

    private Mono<Void> acknowledge(String streamKey, ObjectRecord<String, String> message) {
        return redisTemplate.opsForStream()
                .acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId())
                .then();
    }

    private boolean isBusyGroup(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
