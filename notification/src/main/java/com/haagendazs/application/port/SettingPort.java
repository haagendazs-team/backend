package com.haagendazs.application.port;

import reactor.core.publisher.Mono;

public interface SettingPort {
    Mono<Boolean> isAlertEnabled(Long memberId, String eventTypeCode);
}
