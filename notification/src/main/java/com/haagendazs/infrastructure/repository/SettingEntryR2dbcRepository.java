package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.SettingEntry;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SettingEntryR2dbcRepository extends ReactiveCrudRepository<SettingEntry, Long> {

    Mono<SettingEntry> findByMemberIdAndEventTypeCode(Long memberId, String eventTypeCode);

    @Query("SELECT member_id FROM notification.setting_entries WHERE event_type_code = :eventTypeCode AND is_enabled = true LIMIT :limit OFFSET :offset")
    Flux<Long> findMemberIdsByEventTypeCode(String eventTypeCode, long offset, int limit);
}
