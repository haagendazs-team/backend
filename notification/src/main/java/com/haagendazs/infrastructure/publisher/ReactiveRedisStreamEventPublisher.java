package com.haagendazs.infrastructure.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveRedisStreamEventPublisher {

    public static final String PAYLOAD_KEY = "payload";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Void> publish(Object envelope) {
        return publish(List.of(envelope));
    }

    public Mono<Void> publish(List<?> envelopes) {
        if (envelopes.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(envelopes)
                .flatMap(envelope -> {
                    try {
                        String json = objectMapper.writeValueAsString(envelope);
                        return redisTemplate.opsForStream().add(RedisStreamsConfig.STREAM_KEY, Map.of(PAYLOAD_KEY, json));
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException("알림 payload 직렬화 실패", e));
                    }
                })
                .then()
                .doOnSuccess(v -> log.info("알림 이벤트 발행 stream={} count={}", RedisStreamsConfig.STREAM_KEY, envelopes.size()))
                .doOnError(e -> log.warn("알림 이벤트 발행 실패 stream={}", RedisStreamsConfig.STREAM_KEY, e));
    }

    public Mono<Void> republish(String rawPayload) {
        return redisTemplate.opsForStream()
                .add(RedisStreamsConfig.STREAM_KEY, Map.of(PAYLOAD_KEY, rawPayload))
                .then()
                .doOnSuccess(v -> log.info("알림 이벤트 재발행 stream={}", RedisStreamsConfig.STREAM_KEY))
                .doOnError(e -> log.warn("알림 이벤트 재발행 실패 stream={}", RedisStreamsConfig.STREAM_KEY, e));
    }
}
