package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.SettingEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SettingEntryRepository {
    Mono<SettingEntry> save(SettingEntry entry);
    Mono<SettingEntry> findByMemberIdAndEventTypeCode(Long memberId, String eventTypeCode);
    Flux<Long> findMemberIdsByEventTypeCode(String eventTypeCode, long offset, int limit);
}
