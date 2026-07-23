package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.EventTypeDefinition;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface EventTypeR2dbcRepository extends ReactiveCrudRepository<EventTypeDefinition, String> {

    @Query("SELECT * FROM notification.event_types WHERE is_enabled = true")
    Flux<EventTypeDefinition> findAllEnabled();
}
