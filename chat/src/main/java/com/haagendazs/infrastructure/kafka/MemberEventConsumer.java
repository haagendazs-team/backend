package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.event.MemberDeletedEvent;
import com.haagendazs.application.dto.event.MemberUpdatedEvent;
import com.haagendazs.application.service.ChatMemberSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEventConsumer {

    private final ChatMemberSyncService chatMemberSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "member.updated.v1", groupId = "chat")
    public void consumeMemberUpdated(String message) {
        try {
            MemberUpdatedEvent event = objectMapper.readValue(message, MemberUpdatedEvent.class);
            chatMemberSyncService.handleMemberUpdated(event);
        } catch (Exception e) {
            log.error("member-updated 이벤트 처리 실패, message={}", message, e);
        }
    }

    @KafkaListener(topics = "member.deleted.v1", groupId = "chat")
    public void consumeMemberDeleted(String message) {
        try {
            MemberDeletedEvent event = objectMapper.readValue(message, MemberDeletedEvent.class);
            chatMemberSyncService.handleMemberDeleted(event);
        } catch (Exception e) {
            log.error("member-deleted 이벤트 처리 실패, message={}", message, e);
        }
    }
}
