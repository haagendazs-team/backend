package com.haagendazs.application.port;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.Setting;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SseNotificationPort {
    Flux<ServerSentEvent<Object>> subscribe(Long memberId, Setting setting, List<Channel> channels);
    void send(Long memberId, Object data);
    List<Channel> getCachedChannels(Long memberId);
    boolean isConnected(Long memberId);
}
