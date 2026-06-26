package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.History;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface HistoryRepository {
    Mono<History> save(History history);
    Flux<History> saveAll(List<History> histories);
}
