package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Event;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface EventR2dbcRepository extends ReactiveCrudRepository<Event, Long> {

    Mono<Event> findByStreamMessageId(String streamMessageId);

    @Query("SELECT * FROM notification.events WHERE status IN ('PENDING', 'FAILED') AND scheduled_at <= :now AND retry_count < :maxRetry LIMIT 100")
    Flux<Event> findDueScheduledEvents(LocalDateTime now, int maxRetry);

    @Query("SELECT * FROM notification.events WHERE status = 'PROCESSING' AND updated_at <= :updatedBefore AND stuck_retry_count < :maxStuckRetry LIMIT 100")
    Flux<Event> findStuckProcessingEvents(LocalDateTime updatedBefore, int maxStuckRetry);
}
