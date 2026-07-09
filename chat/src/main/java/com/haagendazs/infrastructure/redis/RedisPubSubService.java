package com.haagendazs.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.ChatMessageSendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPubSubService implements MessageListener {

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String channel, String message) {
        stringRedisTemplate.convertAndSend(channel, message);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            ChatMessageSendResponse response = objectMapper.readValue(payload, ChatMessageSendResponse.class);
            messagingTemplate.convertAndSend("/topic/" + response.channelId(), response);
        } catch (Exception e) {
            log.error("Redis 메시지 처리 실패", e);
        }
    }
}





