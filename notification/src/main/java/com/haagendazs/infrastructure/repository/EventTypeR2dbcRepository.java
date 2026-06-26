package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.EventTypeDefinition;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventTypeR2dbcRepository extends ReactiveCrudRepository<EventTypeDefinition, String> {

    Mono<Boolean> existsByStreamKey(String streamKey);

    @Query("SELECT * FROM notification.event_types WHERE is_enabled = true")
    Flux<EventTypeDefinition> findAllEnabled();
}
