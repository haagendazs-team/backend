package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.event.WorkspaceMemberJoinedEvent;
import com.haagendazs.application.service.ChatMemberSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceMemberEventConsumer {
    private final ChatMemberSyncService chatMemberSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "workspace-member.joined.v1", groupId = "chat")
    public void consumeWorkspaceMemberJoined(String message) {
        try {
            WorkspaceMemberJoinedEvent event = objectMapper.readValue(message, WorkspaceMemberJoinedEvent.class);
            chatMemberSyncService.handleWorkspaceMemberJoined(event);
        } catch (Exception e) {
            log.error("workspace-member-joined 이벤트 처리 실패, message={}", message, e);
        }
    }

}
