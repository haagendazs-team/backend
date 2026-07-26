package com.haagendazs.application.service;

import com.haagendazs.application.dto.event.ChannelMemberDeletedEvent;
import com.haagendazs.application.dto.event.ChannelMemberJoinedEvent;
import com.haagendazs.domain.model.ChatParticipant;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatParticipantSyncService {

    private final ChatParticipantRepository chatParticipantRepository;

    @Transactional
    public void handleChannelMemberJoined(ChannelMemberJoinedEvent event) {
        if (chatParticipantRepository.existsByChannelIdAndMemberId(event.channelId(), event.memberId())) {
            log.warn("이미 존재하는 chat_participant, channelId={}, memberId={}",
                    event.channelId(), event.memberId());
            return;
        }
        ChatParticipant participant = ChatParticipant.builder()
                .channelId(event.channelId())
                .memberId(event.memberId())
                .build();
        chatParticipantRepository.save(participant);
        log.info("chat_participant 생성 완료, channelId={}, memberId={}",
                event.channelId(), event.memberId());
    }

    @Transactional
    public void handleChannelMemberDeleted(ChannelMemberDeletedEvent event) {
        chatParticipantRepository.deleteByChannelIdAndMemberId(event.channelId(), event.memberId());
        log.info("chat_participant 삭제 완료, channelId={}, memberId={}",
                event.channelId(), event.memberId());
    }
}
