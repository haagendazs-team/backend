package com.haagendazs.application.port;

import com.haagendazs.domain.model.SettingEntry;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SettingCachePort {
    Mono<Boolean> isCached(Long memberId);
    Mono<Boolean> isAlertEnabled(Long memberId, String eventTypeCode);
    Mono<Void> putAll(Long memberId, List<SettingEntry> entries);
    Mono<Void> put(Long memberId, String eventTypeCode, boolean enabled);
    Mono<Void> evict(Long memberId);
}
