package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.domain.repository.SettingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class SettingEntryRepositoryAdapter implements SettingEntryRepository {

    private final SettingEntryR2dbcRepository r2dbcRepository;

    @Override
    public Mono<SettingEntry> save(SettingEntry entry) {
        return r2dbcRepository.save(entry);
    }

    @Override
    public Mono<SettingEntry> findByMemberIdAndEventTypeCode(Long memberId, String eventTypeCode) {
        return r2dbcRepository.findByMemberIdAndEventTypeCode(memberId, eventTypeCode);
    }

    @Override
    public Flux<Long> findMemberIdsByEventTypeCode(String eventTypeCode, long offset, int limit) {
        return r2dbcRepository.findMemberIdsByEventTypeCode(eventTypeCode, offset, limit);
    }
}
