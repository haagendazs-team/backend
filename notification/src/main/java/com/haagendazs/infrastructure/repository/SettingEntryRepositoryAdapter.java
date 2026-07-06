package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.domain.repository.SettingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

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
    public Flux<SettingEntry> findAllByMemberId(Long memberId) {
        return r2dbcRepository.findAllByMemberId(memberId);
    }

    @Override
    public Flux<Long> findMemberIdsByEventTypeCode(String eventTypeCode, long offset, int limit) {
        return r2dbcRepository.findMemberIdsByEventTypeCode(eventTypeCode, offset, limit);
    }

    @Override
    public Flux<Long> findDisabledMemberIdsByEventTypeCode(List<Long> memberIds, String eventTypeCode) {
        return r2dbcRepository.findDisabledMemberIdsByEventTypeCode(memberIds, eventTypeCode);
    }
}
