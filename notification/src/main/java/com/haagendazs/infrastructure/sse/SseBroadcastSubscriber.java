package com.haagendazs.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import com.haagendazs.presentation.dto.NotificationResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseBroadcastSubscriber {

    private final ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer;
    private final SseEmitterManager sseEmitterManager;
    private final ChannelTopic sseBroadcastTopic;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void register() {
        reactiveRedisMessageListenerContainer.receive(sseBroadcastTopic)
                .subscribe(message -> handleBroadcast(message.getMessage()));
        log.info("SSE 브로드캐스트 구독 시작 channel={}", RedisPubSubConfig.SSE_BROADCAST_CHANNEL);
    }

    private void handleBroadcast(String body) {
        int separatorIndex = body.indexOf(':');
        if (separatorIndex < 1) {
            log.warn("SSE 브로드캐스트 메시지 파싱 실패 body={}", body);
            return;
        }
        try {
            Long memberId = Long.parseLong(body.substring(0, separatorIndex));
            String json = body.substring(separatorIndex + 1);
            NotificationResponse response = objectMapper.readValue(json, NotificationResponse.class);
            sseEmitterManager.sendLocal(memberId, response);
        } catch (Exception e) {
            log.error("SSE 브로드캐스트 처리 실패 body={}", body, e);
        }
    }
}
