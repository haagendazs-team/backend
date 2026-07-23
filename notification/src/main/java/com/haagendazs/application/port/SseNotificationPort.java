package com.haagendazs.application.port;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SseNotificationPort {
    Flux<ServerSentEvent<Object>> subscribe(Long memberId, Flux<ServerSentEvent<Object>> replay);
    void send(Long memberId, Object data);
    Mono<Boolean> isConnected(Long memberId);
}
