package com.haagendazs.infrastructure.cache;

import com.haagendazs.application.port.SettingCachePort;
import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisSettingCacheAdapter implements SettingCachePort {

    private static final String KEY_PREFIX = "sse:settings:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final NotificationProperties properties;

    @Override
    public Mono<Boolean> isCached(Long memberId) {
        return redisTemplate.hasKey(KEY_PREFIX + memberId);
    }

    @Override
    public Mono<Boolean> isAlertEnabled(Long memberId, String eventTypeCode) {
        return redisTemplate.<String, String>opsForHash()
                .get(KEY_PREFIX + memberId, eventTypeCode)
                .map(Boolean::parseBoolean)
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Mono<Void> putAll(Long memberId, List<SettingEntry> entries) {
        if (entries.isEmpty()) {
            return Mono.empty();
        }
        Map<String, String> fieldValues = entries.stream()
                .collect(Collectors.toMap(
                        SettingEntry::getEventTypeCode,
                        e -> String.valueOf(e.isEnabled())
                ));
        Duration ttl = Duration.ofMillis(properties.sse().timeoutMs()).plusMinutes(1);
        String key = KEY_PREFIX + memberId;
        return redisTemplate.<String, String>opsForHash()
                .putAll(key, fieldValues)
                .then(redisTemplate.expire(key, ttl))
                .then();
    }

    @Override
    public Mono<Void> put(Long memberId, String eventTypeCode, boolean enabled) {
        return redisTemplate.<String, String>opsForHash()
                .put(KEY_PREFIX + memberId, eventTypeCode, String.valueOf(enabled))
                .then();
    }

    @Override
    public Mono<Void> evict(Long memberId) {
        return redisTemplate.delete(KEY_PREFIX + memberId).then();
    }
}
