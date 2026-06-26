package com.haagendazs.infrastructure.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
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
    private final EventTypeRegistry registry;

    public Mono<Void> publish(String eventTypeCode, Map<String, Object> payload) {
        return publish(eventTypeCode, List.of(payload));
    }

    public Mono<Void> publish(String eventTypeCode, List<Map<String, Object>> payloads) {
        if (payloads.isEmpty()) {
            return Mono.empty();
        }
        String streamKey = registry.getByCode(eventTypeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_TYPE_NOT_FOUND))
                .getStreamKey();
        return Flux.fromIterable(payloads)
                .flatMap(p -> {
                    try {
                        String json = objectMapper.writeValueAsString(p);
                        return redisTemplate.opsForStream().add(streamKey, Map.of(PAYLOAD_KEY, json));
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException(
                                "알림 payload 직렬화 실패 eventType=" + eventTypeCode, e));
                    }
                })
                .then()
                .doOnSuccess(v -> log.info("알림 이벤트 발행 stream={} count={}", streamKey, payloads.size()))
                .doOnError(e -> log.warn("알림 이벤트 발행 실패 eventType={}", eventTypeCode, e));
    }

    public Mono<Void> republish(String streamKey, String rawPayload) {
        return redisTemplate.opsForStream()
                .add(streamKey, Map.of(PAYLOAD_KEY, rawPayload))
                .then()
                .doOnSuccess(v -> log.info("알림 이벤트 재발행 stream={}", streamKey))
                .doOnError(e -> log.warn("알림 이벤트 재발행 실패 stream={}", streamKey, e));
    }
}
