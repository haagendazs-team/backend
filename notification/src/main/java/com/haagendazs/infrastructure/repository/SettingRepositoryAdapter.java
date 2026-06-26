package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Setting;
import com.haagendazs.domain.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class SettingRepositoryAdapter implements SettingRepository {

    private final SettingR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Setting> save(Setting setting) {
        return r2dbcRepository.save(setting);
    }

    @Override
    public Mono<Setting> findByMemberId(Long memberId) {
        return r2dbcRepository.findByMemberId(memberId);
    }

    @Override
    public Flux<Long> findMemberIdsByTicketOpenAlertTrue(long offset, int limit) {
        return r2dbcRepository.findMemberIdsByTicketOpenAlertTrue(offset, limit);
    }

    @Override
    public Flux<Long> findMemberIdsByGameStartAlertTrue(long offset, int limit) {
        return r2dbcRepository.findMemberIdsByGameStartAlertTrue(offset, limit);
    }

    @Override
    public Mono<Long> countByTicketOpenAlertTrue() {
        return r2dbcRepository.countByTicketOpenAlertTrue();
    }

    @Override
    public Mono<Long> countByGameStartAlertTrue() {
        return r2dbcRepository.countByGameStartAlertTrue();
    }
}
