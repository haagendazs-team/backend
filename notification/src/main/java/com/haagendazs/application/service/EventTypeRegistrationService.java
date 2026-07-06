package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.NotificationErrorCode;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class EventTypeRegistrationService {

    private final EventTypeRepository eventTypeRepository;
    private final EventTypeRegistry eventTypeRegistry;
    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<EventTypeDefinition> register(String code, boolean isScheduled, boolean isSingleTarget) {
        return eventTypeRepository.existsByCode(code)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.<EventTypeDefinition>error(new BusinessException(NotificationErrorCode.EVENT_TYPE_DUPLICATE));
                    }
                    return eventTypeRepository.save(EventTypeDefinition.of(code, isScheduled, isSingleTarget));
                })
                .flatMap(saved -> {
                    eventTypeRegistry.register(saved);
                    return redisTemplate
                            .convertAndSend(RedisPubSubConfig.EVENT_TYPE_REGISTERED_CHANNEL, saved.getCode())
                            .thenReturn(saved);
                });
    }
}
