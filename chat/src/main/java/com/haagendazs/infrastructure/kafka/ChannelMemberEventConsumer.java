package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.event.ChannelMemberDeletedEvent;
import com.haagendazs.application.dto.event.ChannelMemberJoinedEvent;
import com.haagendazs.application.service.ChatParticipantSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelMemberEventConsumer {
    private final ChatParticipantSyncService chatParticipantSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "member.chat.channel_member_joined.v1", groupId = "chat")
    public void consumeChannelMemberJoined(String message) {
        try {
            ChannelMemberJoinedEvent event = objectMapper.readValue(message, ChannelMemberJoinedEvent.class);
            chatParticipantSyncService.handleChannelMemberJoined(event);
        } catch (Exception e) {
            log.error("channel-member-joined 이벤트 처리 실패, message={}", message, e);
        }
    }

    @KafkaListener(topics = "member.chat.channel_member_deleted.v1", groupId = "chat")
    public void consumeChannelMemberDeleted(String message) {
        try {
            ChannelMemberDeletedEvent event = objectMapper.readValue(message, ChannelMemberDeletedEvent.class);
            chatParticipantSyncService.handleChannelMemberDeleted(event);
        } catch (Exception e) {
            log.error("channel-member-deleted 이벤트 처리 실패, message={}", message, e);
        }
    }
}
