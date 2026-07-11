package com.haagendazs.application.service;

import com.haagendazs.application.dto.event.ChannelCreatedEvent;
import com.haagendazs.application.dto.event.ChannelDeletedEvent;
import com.haagendazs.application.dto.event.ChannelUpdatedEvent;
import com.haagendazs.domain.model.ChatChannel;
import com.haagendazs.domain.repository.ChatChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatChannelSyncService {

    private final ChatChannelRepository chatChannelRepository;

    @Transactional
    public void handleChannelCreated(ChannelCreatedEvent event) {
        if(chatChannelRepository.existsById(event.channelId())) {
            log.warn("이미 존재하는 chat_channel_id={}", event.channelId());
            return;
        }
        ChatChannel chatChannel = ChatChannel.builder()
                .channelId(event.channelId())
                .workspaceId(event.workspaceId())
                .channelName(event.channelName())
                .isDirectMessage(event.isDirectMessage())
                .build();
        chatChannelRepository.save(chatChannel);
        log.info("chat_channel 생성 완료, channelId={}", event.channelId());
    }

    @Transactional
    public void handleChannelUpdated(ChannelUpdatedEvent event) {
        //ifPresentOrElse의 경우 Optional 객체에 값이 존재한다면 1번을 실행 그렇지 않다면 2번을 실행합니다.
        chatChannelRepository.findById(event.channelId()).ifPresentOrElse(channel -> {
            channel.updateChannel(event.channelName());
            chatChannelRepository.save(channel);
            log.info("chat_channel 갱신 완료, channelId={}", event.channelId());
        },
                ()-> log.warn("존재하지 않는 channel 갱신입니다. channelId={}", event.channelId())
        );
    }

    @Transactional
    public void handleChannelDeleted(ChannelDeletedEvent event) {
        chatChannelRepository.deleteById(event.channelId());
        log.info("chat_channel 삭제 완료, channelId={}", event.channelId());
    }

}
