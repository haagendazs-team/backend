package com.haagendazs.infrastructure.consumer;

import com.haagendazs.application.service.EventStatusService;
import com.haagendazs.application.service.FanoutService;
import com.haagendazs.application.service.PayloadParser;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamSubscriptionManager {

    private final StreamReceiver<String, ObjectRecord<String, String>> receiver;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final EventStatusService statusService;
    private final FanoutService fanoutService;
    private final EventTypeRegistry registry;
    private final PayloadParser payloadParser;
    private final MeterRegistry meterRegistry;

    @Value("${spring.application.name:app}-consumer-${HOSTNAME:local}")
    private String consumerName;

    private Disposable subscription;
    private Counter consumeTotal;
    private Counter consumeErrorTotal;
    private Timer consumeDuration;

    @PostConstruct
    public void start() {
        consumeTotal = Counter.builder("stream_consume")
                .description("Redis Stream XREADGROUP 처리 완료 수")
                .register(meterRegistry);
        consumeErrorTotal = Counter.builder("stream_consume_error")
                .description("Redis Stream 메시지 처리 실패 수")
                .register(meterRegistry);
        consumeDuration = Timer.builder("stream_consume_duration")
                .description("Redis Stream 메시지 1건 처리 지연")
                .register(meterRegistry);
        createGroupIfAbsent(RedisStreamsConfig.STREAM_KEY);
        subscription = buildReceiveFlux()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> {
                            log.warn("Stream 구독 재연결 시도 attempt={} streamKey={}",
                                    signal.totalRetries() + 1, RedisStreamsConfig.STREAM_KEY);
                            createGroupIfAbsent(RedisStreamsConfig.STREAM_KEY);
                        }))
                .subscribe(
                        v -> {},
                        e -> log.error("Stream 구독 영구 오류 streamKey={}", RedisStreamsConfig.STREAM_KEY, e)
                );
        log.info("Redis Stream 구독 시작 streamKey={}", RedisStreamsConfig.STREAM_KEY);
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    public boolean isSubscribed() {
        return subscription != null && !subscription.isDisposed();
    }

    private static final int CONSUME_CONCURRENCY = 64;

    private Flux<Void> buildReceiveFlux() {
        return receiver.receive(
                        Consumer.from(RedisStreamsConfig.NOTIFICATION_GROUP, consumerName),
                        StreamOffset.create(RedisStreamsConfig.STREAM_KEY, ReadOffset.lastConsumed()))
                .flatMap(this::handleMessage, CONSUME_CONCURRENCY);
    }

    private Flux<Void> handleMessage(ObjectRecord<String, String> message) {
        String rawPayload = message.getValue();
        String streamMessageId = message.getId().getValue();
        long startNs = System.nanoTime();

        String eventTypeCode;
        try {
            eventTypeCode = payloadParser.extractEventTypeCode(rawPayload);
        } catch (Exception e) {
            log.error("envelope 파싱 실패 id={} payload={}", streamMessageId, rawPayload, e);
            consumeErrorTotal.increment();
            return Flux.empty();
        }

        EventTypeDefinition definition = registry.getByCode(eventTypeCode).orElse(null);
        if (definition == null) {
            log.warn("등록되지 않은 eventTypeCode={} id={}", eventTypeCode, streamMessageId);
            consumeErrorTotal.increment();
            return Flux.empty();
        }

        return statusService.saveEventWithStreamMessageId(definition, rawPayload, streamMessageId)
                .flatMapMany(event -> {
                    if (event.isScheduled()) {
                        return acknowledge(message)
                                .doOnSuccess(v -> log.info("예약 알림 저장 완료 ACK eventType={} id={}", eventTypeCode, streamMessageId))
                                .flux();
                    }
                    return fanoutService.fanoutBuffered(event, definition, rawPayload,
                                    RedisStreamsConfig.STREAM_KEY, message.getId())
                            .then(statusService.markEventStatus(event.getId(), false))
                            .doOnSuccess(v -> {
                                consumeTotal.increment();
                                consumeDuration.record(System.nanoTime() - startNs, java.util.concurrent.TimeUnit.NANOSECONDS);
                                log.info("버퍼 enqueue 완료 eventType={} id={}", eventTypeCode, streamMessageId);
                            })
                            .flux();
                })
                .onErrorResume(e -> {
                    consumeErrorTotal.increment();
                    log.error("Stream 처리 실패 eventType={} id={}", eventTypeCode, streamMessageId, e);
                    return Flux.empty();
                });
    }

    private Mono<Void> acknowledge(ObjectRecord<String, String> message) {
        return redisTemplate.opsForStream()
                .acknowledge(RedisStreamsConfig.STREAM_KEY, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId())
                .then();
    }

    private void createGroupIfAbsent(String streamKey) {
        try {
            // XGROUP CREATE <key> <group> 0 MKSTREAM — stream key가 없어도 자동 생성
            java.nio.ByteBuffer keyBuffer = java.nio.ByteBuffer.wrap(streamKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            redisTemplate.execute(connection ->
                    connection.streamCommands().xGroupCreate(
                            keyBuffer,
                            RedisStreamsConfig.NOTIFICATION_GROUP,
                            org.springframework.data.redis.connection.stream.ReadOffset.from("0"),
                            true
                    )
            ).blockFirst();
        } catch (Exception e) {
            if (!isBusyGroup(e)) {
                log.error("Consumer group 생성 실패 stream={}", streamKey, e);
            }
        }
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
