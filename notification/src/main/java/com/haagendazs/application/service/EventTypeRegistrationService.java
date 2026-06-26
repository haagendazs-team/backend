package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import com.haagendazs.infrastructure.consumer.StreamSubscriptionManager;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EventTypeRegistrationService {

    private static final Pattern STREAM_KEY_PATTERN =
            Pattern.compile("^notif:stream:[a-z][a-z0-9.]{1,88}$");

    private final EventTypeRepository eventTypeRepository;
    private final EventTypeRegistry eventTypeRegistry;
    private final StreamSubscriptionManager streamSubscriptionManager;
    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<EventTypeDefinition> register(
            String code,
            String streamKey,
            boolean isScheduled,
            boolean isSingleTarget,
            String memberIdField,
            String scheduledAtField,
            int scheduledOffsetMinutes
    ) {
        if (!STREAM_KEY_PATTERN.matcher(streamKey).matches()) {
            return Mono.error(new BusinessException(ErrorCode.EVENT_TYPE_STREAM_KEY_INVALID));
        }
        return eventTypeRepository.existsByCode(code)
                .flatMap(codeExists -> {
                    if (codeExists) {
                        return Mono.<Boolean>error(new BusinessException(ErrorCode.EVENT_TYPE_DUPLICATE));
                    }
                    return eventTypeRepository.existsByStreamKey(streamKey);
                })
                .flatMap(streamKeyExists -> {
                    if (streamKeyExists) {
                        return Mono.<EventTypeDefinition>error(new BusinessException(ErrorCode.EVENT_TYPE_DUPLICATE));
                    }
                    EventTypeDefinition definition = EventTypeDefinition.of(
                            code, streamKey, isScheduled, isSingleTarget,
                            memberIdField, scheduledAtField, scheduledOffsetMinutes
                    );
                    return eventTypeRepository.save(definition);
                })
                .flatMap(saved -> {
                    eventTypeRegistry.register(saved);
                    streamSubscriptionManager.startSubscription(saved);
                    return redisTemplate
                            .convertAndSend(RedisPubSubConfig.EVENT_TYPE_REGISTERED_CHANNEL, saved.getCode())
                            .thenReturn(saved);
                });
    }
}
