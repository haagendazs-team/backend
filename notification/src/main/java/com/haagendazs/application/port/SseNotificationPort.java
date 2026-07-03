package com.haagendazs.application.port;

import com.haagendazs.domain.model.Channel;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SseNotificationPort {
    Flux<ServerSentEvent<Object>> subscribe(Long memberId, List<Channel> channels, Flux<ServerSentEvent<Object>> replay);
    void send(Long memberId, Object data);
    List<Channel> getCachedChannels(Long memberId);
    Mono<Boolean> isConnected(Long memberId);
}
