package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Setting;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SettingR2dbcRepository extends ReactiveCrudRepository<Setting, Long> {

    Mono<Setting> findByMemberId(Long memberId);

    @Query("SELECT member_id FROM notification.settings WHERE ticket_open_alert = true LIMIT :limit OFFSET :offset")
    Flux<Long> findMemberIdsByTicketOpenAlertTrue(long offset, int limit);

    @Query("SELECT member_id FROM notification.settings WHERE game_start_alert = true LIMIT :limit OFFSET :offset")
    Flux<Long> findMemberIdsByGameStartAlertTrue(long offset, int limit);

    @Query("SELECT COUNT(*) FROM notification.settings WHERE ticket_open_alert = true")
    Mono<Long> countByTicketOpenAlertTrue();

    @Query("SELECT COUNT(*) FROM notification.settings WHERE game_start_alert = true")
    Mono<Long> countByGameStartAlertTrue();
}
