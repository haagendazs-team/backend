package com.haagendazs.infrastructure.redis;

import com.haagendazs.application.port.NotificationStreamQueryPort;
import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RedisStreamQueryAdapter implements NotificationStreamQueryPort {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<String> findPayload(String streamKey, String messageId) {
        return redisTemplate.opsForStream()
                .range(streamKey, Range.closed(messageId, messageId))
                .next()
                .mapNotNull(message -> {
                    Object payload = message.getValue().get(ReactiveRedisStreamEventPublisher.PAYLOAD_KEY);
                    return payload != null ? String.valueOf(payload) : null;
                });
    }
}
