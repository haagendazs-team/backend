package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.event.ChannelCreatedEvent;
import com.haagendazs.application.dto.event.ChannelDeletedEvent;
import com.haagendazs.application.dto.event.ChannelUpdatedEvent;
import com.haagendazs.application.service.ChatChannelSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelEventConsumer {

    private final ChatChannelSyncService chatChannelSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "member.chat.channel_created.v1", groupId = "chat")
    public void consumeChannelCreated(String message) {
        try {
            ChannelCreatedEvent event = objectMapper.readValue(message, ChannelCreatedEvent.class);
            chatChannelSyncService.handleChannelCreated(event);
        } catch (Exception e) {
            log.error("channel-created 이벤트 처리 실패, message={}", message, e);
        }
    }

    @KafkaListener(topics = "member.chat.channel_updated.v1", groupId = "chat")
    public void consumeChannelUpdated(String message) {
        try {
            ChannelUpdatedEvent event = objectMapper.readValue(message, ChannelUpdatedEvent.class);
            chatChannelSyncService.handleChannelUpdated(event);
        } catch (Exception e) {
            log.error("channel-updated 이벤트 처리 실패, message={}", message, e);
        }
    }

    @KafkaListener(topics = "member.chat.channel_deleted.v1", groupId = "chat")
    public void consumeChannelDeleted(String message) {
        try {
            ChannelDeletedEvent event = objectMapper.readValue(message, ChannelDeletedEvent.class);
            chatChannelSyncService.handleChannelDeleted(event);
        } catch (Exception e) {
            log.error("channel-deleted 이벤트 처리 실패, message={}", message, e);
        }
    }
}


