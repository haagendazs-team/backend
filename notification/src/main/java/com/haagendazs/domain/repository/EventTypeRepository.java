package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.EventTypeDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventTypeRepository {
    Mono<EventTypeDefinition> save(EventTypeDefinition definition);
    Mono<EventTypeDefinition> findByCode(String code);
    Flux<EventTypeDefinition> findAllEnabled();
    Mono<Boolean> existsByCode(String code);
}
