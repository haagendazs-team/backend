package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.History;
import com.haagendazs.domain.repository.HistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HistoryRepositoryAdapter implements HistoryRepository {

    private final HistoryR2dbcRepository r2dbcRepository;

    @Override
    public Mono<History> save(History history) {
        return r2dbcRepository.save(history);
    }

    @Override
    public Flux<History> saveAll(List<History> histories) {
        return r2dbcRepository.saveAll(histories);
    }
}
