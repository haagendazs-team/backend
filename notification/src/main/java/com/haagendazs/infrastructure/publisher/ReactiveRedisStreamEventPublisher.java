package com.haagendazs.infrastructure.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveRedisStreamEventPublisher {

    public static final String PAYLOAD_KEY = "payload";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter publishTotal;
    private Counter publishErrorTotal;
    private Timer publishDuration;

    @PostConstruct
    void initMetrics() {
        publishTotal = Counter.builder("stream_publish")
                .description("Redis Stream XADD 성공 수")
                .register(meterRegistry);
        publishErrorTotal = Counter.builder("stream_publish_error")
                .description("Redis Stream XADD 실패 수")
                .register(meterRegistry);
        publishDuration = Timer.builder("stream_publish_duration")
                .description("Redis Stream XADD 지연")
                .register(meterRegistry);
    }

    public Mono<Void> publish(Object envelope) {
        return publish(List.of(envelope));
    }

    public Mono<Void> publish(List<?> envelopes) {
        if (envelopes.isEmpty()) {
            return Mono.empty();
        }
        long startNs = System.nanoTime();
        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> {
                    try {
                        String json = objectMapper.writeValueAsString(envelope);
                        return redisTemplate.opsForStream().add(RedisStreamsConfig.STREAM_KEY, Map.of(PAYLOAD_KEY, json));
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException("알림 payload 직렬화 실패", e));
                    }
                }, 16)
                .then()
                .doOnSuccess(v -> {
                    publishDuration.record(System.nanoTime() - startNs, java.util.concurrent.TimeUnit.NANOSECONDS);
                    publishTotal.increment(envelopes.size());
                    log.info("알림 이벤트 발행 stream={} count={}", RedisStreamsConfig.STREAM_KEY, envelopes.size());
                })
                .doOnError(e -> {
                    publishErrorTotal.increment();
                    log.warn("알림 이벤트 발행 실패 stream={}", RedisStreamsConfig.STREAM_KEY, e);
                });
    }

    /**
     * k6 bulk 발행 전용 — envelope JSON에 eventTypeCode 필드를 top-level에 주입해 Stream에 XADD.
     * StreamSubscriptionManager의 PayloadParser.extractEventTypeCode() 가 이 필드를 읽는다.
     */
    public Mono<Void> publishWithEventTypeCode(List<?> envelopes, String eventTypeCode) {
        if (envelopes.isEmpty()) {
            return Mono.empty();
        }
        long startNs = System.nanoTime();
        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> {
                    try {
                        ObjectNode node = objectMapper.valueToTree(envelope);
                        node.put("eventTypeCode", eventTypeCode);
                        String json = objectMapper.writeValueAsString(node);
                        return redisTemplate.opsForStream().add(RedisStreamsConfig.STREAM_KEY, Map.of(PAYLOAD_KEY, json));
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException("k6 bulk payload 직렬화 실패", e));
                    }
                }, 16)
                .then()
                .doOnSuccess(v -> {
                    publishDuration.record(System.nanoTime() - startNs, java.util.concurrent.TimeUnit.NANOSECONDS);
                    publishTotal.increment(envelopes.size());
                    log.info("k6 bulk 발행 stream={} count={} eventType={}", RedisStreamsConfig.STREAM_KEY, envelopes.size(), eventTypeCode);
                })
                .doOnError(e -> {
                    publishErrorTotal.increment();
                    log.warn("k6 bulk 발행 실패 stream={}", RedisStreamsConfig.STREAM_KEY, e);
                });
    }

    public Mono<Void> republish(String rawPayload) {
        return redisTemplate.opsForStream()
                .add(RedisStreamsConfig.STREAM_KEY, Map.of(PAYLOAD_KEY, rawPayload))
                .then()
                .doOnSuccess(v -> {
                    publishTotal.increment();
                    log.info("알림 이벤트 재발행 stream={}", RedisStreamsConfig.STREAM_KEY);
                })
                .doOnError(e -> {
                    publishErrorTotal.increment();
                    log.warn("알림 이벤트 재발행 실패 stream={}", RedisStreamsConfig.STREAM_KEY, e);
                });
    }
}
