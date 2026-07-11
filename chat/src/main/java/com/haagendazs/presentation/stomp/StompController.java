package com.haagendazs.presentation.stomp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.ChatMessageSendRequest;
import com.haagendazs.application.dto.ChatMessageSendResponse;
import com.haagendazs.application.service.ChatService;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.infrastructure.redis.RedisPubSubService;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StompController {
    private final ChatService chatService;
    private final RedisPubSubService redisPubSubService;
    private final ObjectMapper objectMapper;

    @MessageMapping("/{channelId}")
    public void sendMessage(@DestinationVariable Long channelId, ChatMessageSendRequest request, SimpMessageHeaderAccessor headerAccessor)
    {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null || sessionAttributes.get("memberId") == null) {
            throw new BusinessException(ChatErrorCode.INVALID_STOMP_TOKEN_HEADER);
        }
        Long memberId = (Long) sessionAttributes.get("memberId");
        ChatMessageSendResponse response = chatService.saveMessage(channelId, request, memberId);

        try{
            String jsonMessage = objectMapper.writeValueAsString(response);
            redisPubSubService.publish("chat:" + channelId, jsonMessage);

        }catch (Exception e){
            log.error("Json 메시지 오류, channelId{}", channelId, e);
            throw new BusinessException(ChatErrorCode.CHAT_NOT_FOUND);
        }
    }

}
