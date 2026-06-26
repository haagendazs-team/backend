package com.haagendazs.application.port;

import reactor.core.publisher.Mono;

public interface NotificationStreamQueryPort {
    Mono<String> findPayload(String streamKey, String messageId);
}
