package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Setting;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SettingRepository {
    Mono<Setting> save(Setting setting);
    Mono<Setting> findByMemberId(Long memberId);
    Flux<Long> findMemberIdsByTicketOpenAlertTrue(long offset, int limit);
    Flux<Long> findMemberIdsByGameStartAlertTrue(long offset, int limit);
    Mono<Long> countByTicketOpenAlertTrue();
    Mono<Long> countByGameStartAlertTrue();
}
