package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.History;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface HistoryR2dbcRepository extends ReactiveCrudRepository<History, Long> {
}
