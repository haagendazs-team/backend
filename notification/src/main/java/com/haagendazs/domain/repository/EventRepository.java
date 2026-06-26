package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Event;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository {
    Mono<Event> save(Event event);
    Mono<Event> findById(Long id);
    Mono<Event> findByStreamMessageId(String streamMessageId);
    Flux<Event> findAllById(List<Long> ids);
    Flux<Event> findDueScheduledEvents(LocalDateTime now, int maxRetry);
    Flux<Event> findStuckProcessingEvents(LocalDateTime updatedBefore, int maxStuckRetry);
}
