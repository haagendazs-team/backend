package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.EventTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class EventTypeRepositoryAdapter implements EventTypeRepository {

    private final EventTypeR2dbcRepository r2dbcRepository;

    @Override
    public Mono<EventTypeDefinition> save(EventTypeDefinition definition) {
        return r2dbcRepository.save(definition);
    }

    @Override
    public Mono<EventTypeDefinition> findByCode(String code) {
        return r2dbcRepository.findById(code);
    }

    @Override
    public Flux<EventTypeDefinition> findAllEnabled() {
        return r2dbcRepository.findAllEnabled();
    }

    @Override
    public Mono<Boolean> existsByCode(String code) {
        return r2dbcRepository.existsById(code);
    }

    @Override
    public Mono<Boolean> existsByStreamKey(String streamKey) {
        return r2dbcRepository.existsByStreamKey(streamKey);
    }
}
