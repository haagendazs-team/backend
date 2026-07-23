package com.haagendazs.application.service;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCreatedService {

    private final ChannelRepository channelRepository;
    private final SettingEntryRepository settingEntryRepository;
    private final EventTypeRegistry eventTypeRegistry;

    public Mono<Void> initialize(Long memberId, String email) {
        return initializeChannels(memberId, email)
                .then(initializeSettings(memberId))
                .doOnSuccess(v -> log.info("회원 알림 초기화 완료 memberId={}", memberId))
                .doOnError(e -> log.error("회원 알림 초기화 실패 memberId={}", memberId, e));
    }

    private Mono<Void> initializeChannels(Long memberId, String email) {
        return channelRepository.save(Channel.create(memberId, ChannelType.EMAIL, email))
                .onErrorResume(e -> {
                    log.warn("채널 초기화 중복 무시 memberId={} type={}", memberId, ChannelType.EMAIL);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> initializeSettings(Long memberId) {
        return Flux.fromIterable(eventTypeRegistry.getAllDefinitions())
                .filter(def -> def.isEnabled())
                .flatMap(def -> settingEntryRepository
                        .findByMemberIdAndEventTypeCode(memberId, def.getCode())
                        .switchIfEmpty(settingEntryRepository.save(SettingEntry.create(memberId, def.getCode())))
                        .onErrorResume(e -> {
                            log.warn("셋팅 초기화 중복 무시 memberId={} code={}", memberId, def.getCode());
                            return Mono.empty();
                        }))
                .then();
    }
}
