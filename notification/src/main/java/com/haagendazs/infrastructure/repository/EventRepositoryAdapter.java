package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EventRepositoryAdapter implements EventRepository {

    private final EventR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Event> save(Event event) {
        return r2dbcRepository.save(event);
    }

    @Override
    public Mono<Event> findById(Long id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Event> findByStreamMessageId(String streamMessageId) {
        return r2dbcRepository.findByStreamMessageId(streamMessageId);
    }

    @Override
    public Flux<Event> findAllById(List<Long> ids) {
        return r2dbcRepository.findAllById(ids);
    }

    @Override
    public Flux<Event> findDueScheduledEvents(LocalDateTime now, int maxRetry) {
        return r2dbcRepository.findDueScheduledEvents(now, maxRetry);
    }

    @Override
    public Flux<Event> findStuckProcessingEvents(LocalDateTime updatedBefore, int maxStuckRetry) {
        return r2dbcRepository.findStuckProcessingEvents(updatedBefore, maxStuckRetry);
    }
}
